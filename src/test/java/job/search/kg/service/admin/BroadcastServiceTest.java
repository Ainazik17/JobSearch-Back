package job.search.kg.service.admin;

import job.search.kg.dto.request.admin.BroadcastRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BroadcastServiceTest {

    private final BroadcastService service = new BroadcastService(null, null, null);

    @Test
    void escapesAdminTextForTelegramHtml() {
        BroadcastRequest r = new BroadcastRequest();
        r.setEmoji("🎬");
        r.setTitle("Сериал <Work KG> & друзья");
        r.setMessage("Смотрите 1-ю серию <бесплатно>");
        r.setFooter("До встречи");
        assertEquals("🎬\n<b>Сериал &lt;Work KG&gt; &amp; друзья</b>\n\nСмотрите 1-ю серию &lt;бесплатно&gt;"
                + "\n\n─────────────────\n<i>До встречи</i>", service.buildMessage(r));
    }
}
