package job.search.kg.telegram.notification;

import job.search.kg.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ListingButtonsTest {

    private ListingButtons buttons;

    @BeforeEach
    void setUp() {
        buttons = new ListingButtons();
        ReflectionTestUtils.setField(buttons, "webAppUrl", "https://workkg.com");
    }

    @SuppressWarnings("unchecked")
    private static String webAppUrl(Map<String, Object> b) {
        return (String) ((Map<String, Object>) b.get("web_app")).get("url");
    }

    @Test
    void proGetsWhatsAppTelegramAndDetails() {
        var rows = buttons.build(ListingButtons.Kind.VACANCY, 42L, true,
                "+996 700 123 456", "@tash_kitchen", "Здравствуйте! Пишу по вакансии «Официант» из Work KG",
                User.Language.RU);

        assertEquals(3, rows.size()); // + «Настроить уведомления»
        List<Map<String, Object>> contacts = rows.get(0);
        assertEquals("💬 WhatsApp", contacts.get(0).get("text"));
        String wa = (String) contacts.get(0).get("url");
        assertTrue(wa.startsWith("https://wa.me/996700123456?text="), wa);
        assertFalse(wa.contains(" "), "текст для WhatsApp должен быть закодирован");
        assertEquals("https://t.me/tash_kitchen", contacts.get(1).get("url"));
        assertEquals("https://workkg.com/?open=vac_42", webAppUrl(rows.get(1).get(0)));
    }

    @Test
    void nonProGetsNoContactsOnlyDetailsAndPro() {
        var rows = buttons.build(ListingButtons.Kind.RESUME, 7L, false,
                "+996700123456", "aigerim", "hi", User.Language.RU);

        assertEquals(3, rows.size()); // + «Настроить уведомления»
        rows.forEach(r -> r.forEach(b -> assertNull(b.get("url"), "без PRO прямых ссылок быть не должно")));
        assertEquals("https://workkg.com/?open=res_7", webAppUrl(rows.get(0).get(0)));
        assertEquals("https://workkg.com/?open=pro", webAppUrl(rows.get(1).get(0)));
        assertEquals("💎 Открыть контакты · PRO", rows.get(1).get(0).get("text"));
        assertEquals("https://workkg.com/?open=notify", webAppUrl(rows.get(2).get(0)));
    }

    @Test
    void proWithoutPhoneAndUsernameStillGetsDetails() {
        var rows = buttons.build(ListingButtons.Kind.VACANCY, 1L, true,
                null, "  ", "hi", User.Language.KY);

        assertEquals(2, rows.size()); // «Подробнее» + «Настроить уведомления»
        assertEquals("📄 Толугураак", rows.get(0).get(0).get("text"));
        assertEquals("🔕 Билдирүүлөрдү жөндөө", rows.get(1).get(0).get("text"));
    }
}
