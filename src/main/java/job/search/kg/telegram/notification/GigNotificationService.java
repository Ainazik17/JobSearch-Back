package job.search.kg.telegram.notification;

import job.search.kg.entity.Gig;
import job.search.kg.entity.User;
import job.search.kg.entity.WorkerOffer;
import job.search.kg.repo.GigRepository;
import job.search.kg.repo.WorkerOfferRepository;
import job.search.kg.service.user.BotSubscriptionService;
import job.search.kg.service.user.BotWorkerOfferService;
import job.search.kg.telegram.TelegramService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Новое разовое задание → исполнителям с «Моими услугами» в том же городе.
 * Обычное задание — только тем, чья профессия встречается в названии задания.
 * «⚡ Срочно» — всем активным исполнителям города («пришлём уведомление тем, кто рядом»).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GigNotificationService {

    private static final ZoneId BISHKEK = ZoneId.of("Asia/Bishkek");

    private final GigRepository gigRepository;
    private final WorkerOfferRepository offerRepository;
    private final TelegramService telegramService;
    private final BotSubscriptionService botSubscriptionService;
    private final ListingButtons listingButtons;

    @Async
    @Transactional(readOnly = true)
    public void notifyWorkersAboutNewGig(Long gigId) {
        try {
            Gig gig = gigRepository.findByIdWithUser(gigId).orElse(null);
            if (gig == null || !Boolean.TRUE.equals(gig.getIsActive())) return;

            List<WorkerOffer> offers = offerRepository.findActive(gig.getCity().getId());
            int sent = 0;
            for (WorkerOffer offer : offers) {
                User worker = offer.getUser();
                if (worker.getTelegramId().equals(gig.getUser().getTelegramId())) continue;
                if (Boolean.TRUE.equals(worker.getIsBanned())) continue;
                if (Boolean.FALSE.equals(worker.getNotifyGigs())) continue; // отключил подработку в настройках
                if (!Boolean.TRUE.equals(gig.getUrgent()) && !matches(gig.getTitle(), offer.getSkills())) continue;

                boolean hasSubscription = isPro(worker.getTelegramId());
                telegramService.sendMessageWithKeyboard(
                        worker.getTelegramId(),
                        buildMessage(gig, worker.getLanguage()),
                        listingButtons.build(
                                ListingButtons.Kind.GIG,
                                gig.getId(),
                                hasSubscription,
                                gig.getPhone(),
                                gig.getUser().getUsername(),
                                "Здравствуйте! Пишу по заданию «" + gig.getTitle() + "» из Work KG",
                                worker.getLanguage()
                        )
                );
                sent++;
            }
            log.info("Notified {} workers about new gig {} (urgent={})", sent, gigId, gig.getUrgent());
        } catch (Exception e) {
            log.error("Error notifying workers about new gig {}: {}", gigId, e.getMessage(), e);
        }
    }

    /**
     * Группы синонимов: если в «Моих услугах» есть слово с основой из ключей группы,
     * исполнитель подходит и под задания со словами из самой группы.
     * «Няня» ↔ «посидеть с ребёнком», «Уборщица» ↔ «помыть окна», «Грузчик» ↔ «переезд».
     */
    private static final List<List<String>> SYNONYMS = List.of(
            List.of("грузч", "переезд", "погруз", "разгруз", "перенест", "перенос", "таскат", "поднят"),
            List.of("курьер", "достав", "отвезт", "привезт", "забрат", "передат"),
            List.of("убор", "клининг", "горничн", "убрат", "помыт", "мыть", "мойк", "окн", "генеральн"),
            List.of("нян", "ребен", "ребенк", "дет", "посидет", "присмотр"),
            List.of("сантех", "кран", "смесит", "унитаз", "труб", "засор", "бойлер", "раковин"),
            List.of("электр", "розет", "провод", "люстр", "выключат", "счетчик"),
            List.of("сборщик", "сборк", "собрат", "шкаф", "мебел", "кроват"),
            List.of("репетит", "урок", "заняти", "подготов", "домашн"),
            List.of("официант", "бармен", "банкет", "свадьб", "обслуж", "кейтеринг"),
            List.of("повар", "приготов", "готовк", "кухн"),
            List.of("водител", "перевез", "подвезт", "газел"),
            List.of("разнораб", "строител", "ремонт", "демонтаж", "копат", "штукатур", "покраск", "покрасит"),
            List.of("сиделк", "ухаживат", "уход", "пожил"),
            List.of("садовник", "газон", "огород", "двор", "покос", "покосит"),
            List.of("мастер на час", "повесит", "полк", "карниз", "починит")
    );

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replace('ё', 'е');
    }

    /** Основа слова: длинные слова без двух последних букв («грузчики» → «грузчи») */
    private static String stem(String word) {
        return word.length() > 5 ? word.substring(0, word.length() - 2) : word;
    }

    /**
     * Подходит ли задание исполнителю: профессия из «Моих услуг» встречается в названии
     * (по основе, в обе стороны: «Грузчик» ↔ «грузчики») или совпадает по группе синонимов.
     */
    static boolean matches(String title, String skills) {
        String t = norm(title);
        String[] titleWords = t.split("[^\\p{L}]+");
        String skillText = norm(String.join(" ", BotWorkerOfferService.splitSkills(skills)));
        for (String word : skillText.split("[^\\p{L}]+")) {
            if (word.length() < 4) continue;
            String st = stem(word);
            if (t.contains(st)) return true;
            for (String tw : titleWords) {
                if (tw.length() >= 5 && word.startsWith(stem(tw))) return true;
            }
        }
        for (List<String> group : SYNONYMS) {
            boolean skillInGroup = group.stream().anyMatch(skillText::contains);
            if (skillInGroup && group.stream().anyMatch(t::contains)) return true;
        }
        return false;
    }

    private String buildMessage(Gig gig, User.Language lang) {
        LocalDate today = LocalDate.now(BISHKEK);
        String day = gig.getWorkDate().equals(today) ? label(lang, "сегодня", "бүгүн", "today")
                : gig.getWorkDate().equals(today.plusDays(1)) ? label(lang, "завтра", "эртең", "tomorrow")
                : gig.getWorkDate().format(DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru")));
        String unit = switch (gig.getPayUnit()) {
            case HOUR -> label(lang, " в час", " саатына", " per hour");
            case SHIFT -> label(lang, " за смену", " сменага", " per shift");
            default -> "";
        };
        StringBuilder msg = new StringBuilder();
        if (Boolean.TRUE.equals(gig.getUrgent())) msg.append("⚡ ");
        msg.append(label(lang, "<b>Подработка рядом</b>", "<b>Жакын жерде убактылуу иш</b>", "<b>Gig nearby</b>")).append("\n\n");
        msg.append("🛠 ").append(escape(gig.getTitle())).append("\n");
        msg.append("🗓 ").append(day);
        if (gig.getTimeText() != null && !gig.getTimeText().isBlank()) msg.append(", ").append(escape(gig.getTimeText()));
        msg.append("\n");
        msg.append("💰 ").append(String.format(Locale.ROOT, "%,d", gig.getPay()).replace(',', ' ')).append(" сом").append(unit).append("\n");
        msg.append("📍 ").append(escape(gig.getAddress()));
        msg.append(VacancyNotificationService.voiceLine(gig.getVoiceUrl(), lang));
        return msg.toString();
    }

    private static String label(User.Language lang, String ru, String ky, String en) {
        return switch (lang) {
            case KY -> ky;
            case EN -> en;
            default -> ru;
        };
    }

    /** parse_mode=HTML: пользовательский текст экранируем */
    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Проверка PRO не должна обрывать рассылку остальным получателям */
    private boolean isPro(Long telegramId) {
        try {
            return botSubscriptionService.hasActiveSubscription(telegramId);
        } catch (Exception e) {
            log.warn("PRO check failed for {}: {}", telegramId, e.getMessage());
            return false;
        }
    }
}
