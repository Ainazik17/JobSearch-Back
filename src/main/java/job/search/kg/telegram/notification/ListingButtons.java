package job.search.kg.telegram.notification;

import job.search.kg.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static job.search.kg.telegram.TelegramService.urlButton;
import static job.search.kg.telegram.TelegramService.webAppButton;

/**
 * Кнопки под уведомлением о новой вакансии/анкете.
 * PRO: WhatsApp + Telegram + «Подробнее». Без PRO: «Подробнее» + «Открыть контакты · PRO»
 * (контакты без подписки не раскрываем — как и в Mini App).
 * «Подробнее» открывает Mini App с ?open=vac_{id} / res_{id} / gig_{id}: фронт сам переходит на карточку.
 */
@Component
public class ListingButtons {

    @Value("${telegram.webapp.url:https://workkg.com}")
    private String webAppUrl;

    public enum Kind { VACANCY, RESUME, GIG }

    public List<List<Map<String, Object>>> build(Kind kind, Long id, boolean hasPro,
                                                 String phone, String telegramUsername,
                                                 String waText, User.Language lang) {
        List<List<Map<String, Object>>> rows = new ArrayList<>();
        String prefix = switch (kind) {
            case VACANCY -> "vac_";
            case RESUME -> "res_";
            case GIG -> "gig_";
        };
        String detailUrl = openUrl(prefix + id);

        if (hasPro) {
            List<Map<String, Object>> contacts = new ArrayList<>();
            String digits = phone == null ? "" : phone.replaceAll("\\D", "");
            if (digits.length() >= 9) {
                contacts.add(urlButton("💬 WhatsApp",
                        "https://wa.me/" + digits + "?text=" + URLEncoder.encode(waText, StandardCharsets.UTF_8)));
            }
            String username = telegramUsername == null ? "" : telegramUsername.trim().replaceFirst("^@", "");
            if (!username.isEmpty()) {
                contacts.add(urlButton("✈️ Telegram", "https://t.me/" + username));
            }
            if (!contacts.isEmpty()) rows.add(contacts);
            rows.add(List.of(webAppButton(detailsLabel(lang), detailUrl)));
        } else {
            rows.add(List.of(webAppButton(detailsLabel(lang), detailUrl)));
            rows.add(List.of(webAppButton(proLabel(lang), openUrl("pro"))));
        }
        rows.add(List.of(webAppButton(settingsLabel(lang), openUrl("notify"))));
        return rows;
    }

    /** Ссылка Mini App, которая сразу открывает нужный экран: ?open=app_12 и т.п. */
    public String openUrl(String target) {
        String base = webAppUrl.endsWith("/") ? webAppUrl : webAppUrl + "/";
        return base + "?open=" + target;
    }

    private static String detailsLabel(User.Language lang) {
        return switch (lang) {
            case KY -> "📄 Толугураак";
            case EN -> "📄 Details";
            default -> "📄 Подробнее";
        };
    }

    private static String settingsLabel(User.Language lang) {
        return switch (lang) {
            case KY -> "🔕 Билдирүүлөрдү жөндөө";
            case EN -> "🔕 Notification settings";
            default -> "🔕 Настроить уведомления";
        };
    }

    private static String proLabel(User.Language lang) {
        return switch (lang) {
            case KY -> "💎 Байланыштар · PRO";
            case EN -> "💎 Unlock contacts · PRO";
            default -> "💎 Открыть контакты · PRO";
        };
    }
}
