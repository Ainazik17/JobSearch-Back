package job.search.kg.service.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Единое правило раскрытия контактов: PRO или своё объявление — полностью, иначе маска (как BotSearchService). */
@Component
@RequiredArgsConstructor
public class ContactMasking {

    private final BotSubscriptionService subscriptionService;

    public boolean canSeeContacts(Long viewerTelegramId, Long ownerTelegramId) {
        if (viewerTelegramId == null) return false;
        if (viewerTelegramId.equals(ownerTelegramId)) return true;
        try {
            return subscriptionService.hasActiveSubscription(viewerTelegramId);
        } catch (Exception e) {
            return false; // незарегистрированный пользователь
        }
    }

    public static String mask(String phone) {
        if (phone == null || phone.isEmpty()) return phone;
        return phone.length() > 6 ? phone.substring(0, 6) + " *** ***" : "*** *** ***";
    }

    /** «Айгерим Касымова» → «Айгерим К.» */
    public static String shortName(String first, String last) {
        String f = first == null ? "" : first.trim();
        String l = last == null ? "" : last.trim();
        if (l.isEmpty()) return f.isEmpty() ? "Пользователь" : f;
        return f + " " + l.charAt(0) + ".";
    }
}
