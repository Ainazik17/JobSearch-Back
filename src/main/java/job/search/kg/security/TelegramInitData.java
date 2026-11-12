package job.search.kg.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Проверка Telegram.WebApp.initData (https://core.telegram.org/bots/webapps#validating-data-received-via-the-mini-app):
 * secret = HMAC_SHA256(key="WebAppData", botToken); hash = hex(HMAC_SHA256(key=secret, data_check_string)).
 */
public final class TelegramInitData {

    private static final Pattern USER_ID = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");

    private TelegramInitData() {
    }

    /** @return id пользователя Telegram, если подпись верна и данные не старше maxAgeSeconds */
    public static Optional<Long> verify(String initData, String botToken, long maxAgeSeconds) {
        if (initData == null || initData.isBlank() || botToken == null || botToken.isBlank()) return Optional.empty();
        Map<String, String> fields = new TreeMap<>();
        String hash = null;
        for (String pair : initData.split("&")) {
            int i = pair.indexOf('=');
            if (i <= 0) continue;
            String k = URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8);
            String v = URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8);
            if (k.equals("hash")) hash = v;
            else fields.put(k, v);
        }
        if (hash == null) return Optional.empty();

        StringJoiner dcs = new StringJoiner("\n");
        fields.forEach((k, v) -> dcs.add(k + "=" + v));
        try {
            byte[] secret = hmac("WebAppData".getBytes(StandardCharsets.UTF_8), botToken.getBytes(StandardCharsets.UTF_8));
            String expected = HexFormat.of().formatHex(hmac(secret, dcs.toString().getBytes(StandardCharsets.UTF_8)));
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), hash.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII))) {
                return Optional.empty();
            }
        } catch (Exception e) {
            return Optional.empty();
        }

        try {
            long authDate = Long.parseLong(fields.getOrDefault("auth_date", "0"));
            if (maxAgeSeconds > 0 && Instant.now().getEpochSecond() - authDate > maxAgeSeconds) return Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }

        Matcher m = USER_ID.matcher(fields.getOrDefault("user", ""));
        return m.find() ? Optional.of(Long.parseLong(m.group(1))) : Optional.empty();
    }

    static byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }
}
