package job.search.kg.service.admin;

import job.search.kg.dto.request.admin.BroadcastRequest;
import job.search.kg.repo.UserRepository;
import job.search.kg.telegram.TelegramService;
import job.search.kg.telegram.notification.ListingButtons;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static job.search.kg.telegram.TelegramService.urlButton;
import static job.search.kg.telegram.TelegramService.webAppButton;

/**
 * Рассылка из админки. Идёт в фоне (тысячи сообщений с лимитом Telegram ~25/с — это минуты,
 * HTTP-запрос столько не ждёт); прогресс — по id рассылки. Одновременно — одна рассылка.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BroadcastService {

    private final UserRepository userRepository;
    private final TelegramService telegramService;
    private final ListingButtons listingButtons;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "broadcast");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private volatile Job running;

    /** Запустить рассылку; IllegalStateException — если предыдущая ещё идёт */
    public synchronized Job start(BroadcastRequest req, String startedBy) {
        validate(req);
        if (running != null && running.status == Status.RUNNING) {
            throw new IllegalStateException("Уже идёт рассылка: отправлено " + running.sent + " из " + running.total);
        }
        List<Long> recipients = recipients(req.getAudience());
        String text = buildMessage(req);
        List<List<Map<String, Object>>> keyboard = keyboard(req);

        Job job = new Job(UUID.randomUUID().toString(), recipients.size(), title(req), startedBy, LocalDateTime.now());
        jobs.put(job.id, job);
        running = job;
        if (jobs.size() > 50) jobs.values().removeIf(j -> j.status != Status.RUNNING && j != job
                && j.startedAt.isBefore(LocalDateTime.now().minusDays(1)));

        executor.submit(() -> {
            for (Long chatId : recipients) {
                if (job.cancelled) break;
                boolean ok;
                try {
                    ok = telegramService.sendMessageWithKeyboard(chatId, text, keyboard); // троттлинг внутри
                } catch (Exception e) {
                    ok = false;
                }
                if (ok) job.sent.incrementAndGet();
                else job.failed.incrementAndGet();
            }
            job.status = job.cancelled ? Status.CANCELLED : Status.DONE;
            job.finishedAt = LocalDateTime.now();
            log.info("Broadcast {} {}: sent={}, failed={}, total={}", job.id, job.status, job.sent, job.failed, job.total);
        });
        log.info("Broadcast {} started by {}: audience={}, total={}", job.id, startedBy, req.getAudience(), job.total);
        return job;
    }

    /** Проверить, как выглядит сообщение: отправить одному (обычно себе) */
    public boolean sendTest(BroadcastRequest req, Long telegramId) {
        validate(req);
        return telegramService.sendMessageWithKeyboard(telegramId, buildMessage(req), keyboard(req));
    }

    public int countRecipients(BroadcastRequest.Audience audience) {
        return recipients(audience).size();
    }

    public Optional<Job> job(String id) {
        return Optional.ofNullable(jobs.get(id));
    }

    public List<Job> recentJobs() {
        return jobs.values().stream().sorted(Comparator.comparing((Job j) -> j.startedAt).reversed()).limit(20).toList();
    }

    public boolean cancel(String id) {
        Job j = jobs.get(id);
        if (j == null || j.status != Status.RUNNING) return false;
        j.cancelled = true;
        return true;
    }

    private List<Long> recipients(BroadcastRequest.Audience audience) {
        BroadcastRequest.Audience a = audience == null ? BroadcastRequest.Audience.ALL : audience;
        List<Long> all = userRepository.findBroadcastRecipients();
        if (a == BroadcastRequest.Audience.ALL) return all;
        Set<Long> pro = new HashSet<>(userRepository.findProRecipients(LocalDateTime.now()));
        return all.stream().filter(id -> a == BroadcastRequest.Audience.PRO ? pro.contains(id) : !pro.contains(id)).toList();
    }

    private static void validate(BroadcastRequest req) {
        boolean empty = isBlank(req.getTitle()) && isBlank(req.getMessage());
        if (empty) throw new IllegalArgumentException("Напишите заголовок или текст");
        if (!isBlank(req.getButtonUrl()) && !req.getButtonUrl().trim().matches("^https://\\S+$")) {
            throw new IllegalArgumentException("Ссылка кнопки должна начинаться с https://");
        }
        if (!isBlank(req.getButtonOpen()) && !req.getButtonOpen().trim().matches("^[a-z_]+(_\\d+)?$")) {
            throw new IllegalArgumentException("Неверный раздел Mini App");
        }
        if ((!isBlank(req.getButtonUrl()) || !isBlank(req.getButtonOpen())) && isBlank(req.getButtonText())) {
            throw new IllegalArgumentException("Укажите текст кнопки");
        }
    }

    private List<List<Map<String, Object>>> keyboard(BroadcastRequest req) {
        if (isBlank(req.getButtonText())) return null;
        String text = req.getButtonText().trim();
        if (!isBlank(req.getButtonOpen())) {
            return List.of(List.of(webAppButton(text, listingButtons.openUrl(req.getButtonOpen().trim()))));
        }
        if (!isBlank(req.getButtonUrl())) return List.of(List.of(urlButton(text, req.getButtonUrl().trim())));
        return null;
    }

    /** parse_mode=HTML: текст админа экранируем — один «<» иначе ломает всю рассылку */
    String buildMessage(BroadcastRequest req) {
        StringBuilder sb = new StringBuilder();
        if (!isBlank(req.getEmoji())) sb.append(esc(req.getEmoji().trim())).append("\n");
        if (!isBlank(req.getTitle())) sb.append("<b>").append(esc(req.getTitle().trim())).append("</b>\n");
        if (!isBlank(req.getSubtitle())) sb.append("<i>").append(esc(req.getSubtitle().trim())).append("</i>\n");
        if (!isBlank(req.getMessage())) sb.append("\n").append(esc(req.getMessage().trim()));
        if (!isBlank(req.getFooter())) {
            sb.append("\n\n─────────────────\n<i>").append(esc(req.getFooter().trim())).append("</i>");
        }
        return sb.toString().trim();
    }

    private static String title(BroadcastRequest req) {
        String t = !isBlank(req.getTitle()) ? req.getTitle() : req.getMessage();
        t = t.trim();
        return t.length() > 80 ? t.substring(0, 80) + "…" : t;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public enum Status { RUNNING, DONE, CANCELLED }

    public static final class Job {
        public final String id;
        public final int total;
        public final String title;
        public final String startedBy;
        public final LocalDateTime startedAt;
        public final AtomicInteger sent = new AtomicInteger();
        public final AtomicInteger failed = new AtomicInteger();
        public volatile Status status = Status.RUNNING;
        public volatile LocalDateTime finishedAt;
        volatile boolean cancelled;

        Job(String id, int total, String title, String startedBy, LocalDateTime startedAt) {
            this.id = id;
            this.total = total;
            this.title = title;
            this.startedBy = startedBy;
            this.startedAt = startedAt;
        }

        public Map<String, Object> view() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("title", title);
            m.put("status", status);
            m.put("total", total);
            m.put("sent", sent.get());
            m.put("failed", failed.get());
            m.put("startedBy", startedBy);
            m.put("startedAt", startedAt);
            m.put("finishedAt", finishedAt);
            return m;
        }
    }
}
