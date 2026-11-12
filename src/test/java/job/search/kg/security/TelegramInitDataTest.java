package job.search.kg.security;

import org.junit.jupiter.api.Test;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TelegramInitDataTest {

    private static final String TOKEN = "123456:TEST-token";

    /** initData так же, как его подписывает Telegram */
    static String sign(String token, long authDate, String userJson) throws Exception {
        String dcs = "auth_date=" + authDate + "\nquery_id=AAE1\nuser=" + userJson;
        byte[] secret = TelegramInitData.hmac("WebAppData".getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
        String hash = HexFormat.of().formatHex(TelegramInitData.hmac(secret, dcs.getBytes(StandardCharsets.UTF_8)));
        return "query_id=AAE1&user=" + URLEncoder.encode(userJson, StandardCharsets.UTF_8)
                + "&auth_date=" + authDate + "&hash=" + hash;
    }

    @Test
    void acceptsValidSignature() throws Exception {
        String data = sign(TOKEN, Instant.now().getEpochSecond(), "{\"id\":1810333455,\"first_name\":\"Тест\"}");
        assertEquals(Optional.of(1810333455L), TelegramInitData.verify(data, TOKEN, 86400));
    }

    @Test
    void rejectsForgedOrStale() throws Exception {
        long now = Instant.now().getEpochSecond();
        String valid = sign(TOKEN, now, "{\"id\":42}");
        // подменили пользователя после подписи
        String forged = valid.replace(URLEncoder.encode("{\"id\":42}", StandardCharsets.UTF_8),
                URLEncoder.encode("{\"id\":43}", StandardCharsets.UTF_8));
        assertTrue(TelegramInitData.verify(forged, TOKEN, 86400).isEmpty());
        // подписано другим ботом
        assertTrue(TelegramInitData.verify(sign("999:other", now, "{\"id\":42}"), TOKEN, 86400).isEmpty());
        // устарело
        assertTrue(TelegramInitData.verify(sign(TOKEN, now - 200_000, "{\"id\":42}"), TOKEN, 86400).isEmpty());
        assertTrue(TelegramInitData.verify(null, TOKEN, 86400).isEmpty());
        assertTrue(TelegramInitData.verify("user=%7B%22id%22%3A42%7D", TOKEN, 86400).isEmpty());
    }
}
