package job.search.kg.service.user;

import job.search.kg.entity.User;
import job.search.kg.exceptions.ResourceNotFoundException;
import job.search.kg.repo.UserRepository;
import job.search.kg.telegram.TelegramService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static job.search.kg.telegram.TelegramService.urlButton;

/**
 * «Написать в Telegram», когда у автора объявления нет username: t.me-ссылку не построить,
 * поэтому бот сам пересылает автору, кто и по какому объявлению хочет связаться,
 * с кликабельным упоминанием (tg://user?id=…) и кнопками WhatsApp / Telegram откликнувшегося.
 */
@Service
@RequiredArgsConstructor
public class ContactRelayService {

    /** Не чаще раза в 10 минут от одного человека по одному объявлению */
    private static final long COOLDOWN_MS = 10 * 60 * 1000;

    private final UserRepository userRepository;
    private final TelegramService telegramService;
    private final ContactMasking contactMasking;
    private final UserContactService userContactService;
    private final Map<String, Long> lastSent = new ConcurrentHashMap<>();

    /**
     * @param subject «по заданию «Грузчики на переезд»» / «по вашим услугам»
     * @return false — уже писали недавно (сообщение не отправлено повторно)
     */
    public boolean relay(Long viewerTelegramId, User owner, String key, String subject, String waText) {
        if (owner.getTelegramId().equals(viewerTelegramId)) {
            throw new IllegalArgumentException("Это ваше объявление");
        }
        if (!contactMasking.canSeeContacts(viewerTelegramId, owner.getTelegramId())) {
            throw new AccessDeniedException("Контакты доступны с PRO");
        }
        User viewer = userRepository.findByTelegramId(viewerTelegramId)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));

        // Без username и номера автору нечем ответить: tg://user?id не кликабелен при закрытой
        // приватности, а кнопок не будет — не шлём «пустое» сообщение, просим указать контакт
        String phone = userContactService.knownPhone(viewer);
        String username = viewer.getUsername() == null ? "" : viewer.getUsername().trim().replaceFirst("^@", "");
        if (phone == null && username.isEmpty()) {
            throw new IllegalArgumentException("Чтобы автор мог вам ответить, укажите номер телефона в профиле "
                    + "или задайте username в настройках Telegram");
        }

        String k = viewerTelegramId + ":" + key;
        long now = System.currentTimeMillis();
        Long prev = lastSent.get(k);
        if (prev != null && now - prev < COOLDOWN_MS) return false;
        lastSent.put(k, now);
        if (lastSent.size() > 10_000) lastSent.entrySet().removeIf(e -> now - e.getValue() > COOLDOWN_MS);

        List<Map<String, Object>> row = new ArrayList<>();
        if (phone != null) {
            row.add(urlButton("💬 WhatsApp", "https://wa.me/" + phone.replaceAll("\\D", "")
                    + "?text=" + URLEncoder.encode(waText, StandardCharsets.UTF_8)));
        }
        if (!username.isEmpty()) row.add(urlButton("✈️ Telegram", "https://t.me/" + username));

        String name = ContactMasking.shortName(viewer.getFirstName(), viewer.getLastName());
        String text = "✉️ <a href=\"tg://user?id=" + viewer.getTelegramId() + "\">" + escape(name) + "</a> "
                + "хочет связаться " + escape(subject) + ".\n\n"
                + (phone != null ? "📞 " + escape(phone) + "\n\n" : "")
                + "Ответьте кнопкой ниже.";
        telegramService.sendMessageWithKeyboard(owner.getTelegramId(), text, List.of(row));
        return true;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
