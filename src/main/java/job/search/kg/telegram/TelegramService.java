package job.search.kg.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramService {

    private static final String TELEGRAM_API_URL = "https://api.telegram.org/bot";
    private final RestTemplate restTemplate;
    @Value("${telegram.bot.token}")
    private String botToken;

    public void sendMessage(Long chatId, String text) {
        String url = TELEGRAM_API_URL + botToken + "/sendMessage";

        Map<String, Object> request = new HashMap<>();
        request.put("chat_id", chatId);
        request.put("text", text);
        request.put("parse_mode", "HTML");

        if (post(url, request, chatId)) log.info("Message sent to user {}", chatId);
    }

    public void sendMessageWithButton(Long chatId, String text, String buttonText, String buttonUrl) {
        String url = TELEGRAM_API_URL + botToken + "/sendMessage";

        Map<String, Object> request = new HashMap<>();
        request.put("chat_id", chatId);
        request.put("text", text);
        request.put("parse_mode", "HTML");

        if (buttonText != null && buttonUrl != null) {
            Map<String, Object> button = Map.of("text", buttonText, "url", buttonUrl);
            Map<String, Object> keyboard = Map.of("inline_keyboard", List.of(List.of(button)));
            request.put("reply_markup", keyboard);
        }

        if (post(url, request, chatId)) log.info("Message with button sent to user {}", chatId);
    }

    /**
     * Сообщение с inline-клавиатурой. rows — ряды кнопок, каждая кнопка — Map
     * в формате Telegram InlineKeyboardButton: {text, url} или {text, web_app: {url}}.
     */
    public void sendMessageWithKeyboard(Long chatId, String text, List<List<Map<String, Object>>> rows) {
        String url = TELEGRAM_API_URL + botToken + "/sendMessage";

        Map<String, Object> request = new HashMap<>();
        request.put("chat_id", chatId);
        request.put("text", text);
        request.put("parse_mode", "HTML");
        request.put("disable_web_page_preview", true);
        if (rows != null && !rows.isEmpty()) {
            request.put("reply_markup", Map.of("inline_keyboard", rows));
        }

        if (post(url, request, chatId)) log.info("Message with keyboard sent to user {}", chatId);
    }

    // ---------- Отправка с учётом лимитов Telegram ----------

    /** Telegram: ~30 сообщений/с на бота. Держим ~25/с на весь процесс (рассылки идут из нескольких потоков). */
    private static final long MIN_INTERVAL_MS = 40;
    private static final Pattern RETRY_AFTER = Pattern.compile("\"retry_after\"\\s*:\\s*(\\d+)");
    private final Object rateLock = new Object();
    private long lastSendAt = 0;

    private void throttle() {
        synchronized (rateLock) {
            long wait = lastSendAt + MIN_INTERVAL_MS - System.currentTimeMillis();
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            lastSendAt = System.currentTimeMillis();
        }
    }

    /**
     * Отправить запрос. 429 Too Many Requests — ждём retry_after (до 30 с) и повторяем один раз.
     * 403 (пользователь заблокировал бота) — не ошибка сервиса, пишем warn.
     * @return true, если Telegram принял сообщение
     */
    private boolean post(String url, Map<String, Object> request, Long chatId) {
        for (int attempt = 0; attempt < 2; attempt++) {
            throttle();
            try {
                restTemplate.postForEntity(url, request, String.class);
                return true;
            } catch (HttpClientErrorException.TooManyRequests e) {
                long sec = retryAfter(e.getResponseBodyAsString());
                log.warn("Telegram 429 for user {}, retry in {} s", chatId, sec);
                if (attempt == 0 && sec <= 30) {
                    try {
                        Thread.sleep(sec * 1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                    continue;
                }
                return false;
            } catch (HttpClientErrorException.Forbidden e) {
                log.warn("User {} blocked the bot or chat is unavailable", chatId);
                return false;
            } catch (Exception e) {
                log.error("Failed to send message to user {}: {}", chatId, safe(e));
                return false;
            }
        }
        return false;
    }

    static long retryAfter(String body) {
        Matcher m = RETRY_AFTER.matcher(body == null ? "" : body);
        return m.find() ? Math.max(1, Long.parseLong(m.group(1))) : 3;
    }

    public static Map<String, Object> urlButton(String text, String url) {
        return Map.of("text", text, "url", url);
    }

    public static Map<String, Object> webAppButton(String text, String url) {
        return Map.of("text", text, "web_app", Map.of("url", url));
    }

    /** Текст ошибки без токена бота: RestTemplate кладёт в сообщение полный URL запроса */
    private String safe(Exception e) {
        String m = String.valueOf(e.getMessage());
        return botToken == null || botToken.isEmpty() ? m : m.replace(botToken, "***");
    }

    public enum Membership { MEMBER, NOT_MEMBER, ERROR }

    /**
     * Подписан ли пользователь на канал. ERROR — Telegram не дал проверить
     * (бот не админ канала, неверный channel_id и т.п.): это не вина пользователя.
     */
    @SuppressWarnings("unchecked")
    public Membership checkMembership(Long userId, String channelId) {
        String url = TELEGRAM_API_URL + botToken + "/getChatMember";
        Map<String, Object> request = new HashMap<>();
        request.put("chat_id", channelId);
        request.put("user_id", userId);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null || !(body.get("result") instanceof Map)) return Membership.ERROR;
            Map<String, Object> result = (Map<String, Object>) body.get("result");
            String status = String.valueOf(result.get("status"));
            if ("member".equals(status) || "administrator".equals(status) || "creator".equals(status)) {
                return Membership.MEMBER;
            }
            // restricted — участник с ограничениями, всё ещё в канале
            if ("restricted".equals(status) && Boolean.TRUE.equals(result.get("is_member"))) return Membership.MEMBER;
            return Membership.NOT_MEMBER;
        } catch (HttpClientErrorException e) {
            String b = e.getResponseBodyAsString();
            // «user not found» / PARTICIPANT_ID_INVALID — пользователя нет в канале
            if (b != null && (b.contains("user not found") || b.contains("PARTICIPANT_ID_INVALID"))) {
                return Membership.NOT_MEMBER;
            }
            log.error("getChatMember failed for user {} in channel {}: {} {}", userId, channelId, e.getStatusCode(), b);
        } catch (Exception e) {
            log.error("getChatMember failed for user {} in channel {}: {}", userId, channelId, safe(e));
        }
        return Membership.ERROR;
    }

    public boolean checkSubscription(Long userId, String channelId) {
        String url = TELEGRAM_API_URL + botToken + "/getChatMember";

        Map<String, Object> request = new HashMap<>();
        request.put("chat_id", channelId);
        request.put("user_id", userId);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> result = (Map<String, Object>) response.getBody().get("result");
                String status = (String) result.get("status");

                return "member".equals(status) ||
                        "administrator".equals(status) ||
                        "creator".equals(status);
            }
        } catch (Exception e) {
            log.error("Failed to check subscription for user {} in channel {}: {}",
                    userId, channelId, safe(e));
        }

        return false;
    }

    public void deleteMessage(Long chatId, Integer messageId) {
        String url = TELEGRAM_API_URL + botToken + "/deleteMessage";

        Map<String, Object> request = new HashMap<>();
        request.put("chat_id", chatId);
        request.put("message_id", messageId);

        try {
            restTemplate.postForEntity(url, request, String.class);
            log.info("Message {} deleted for user {}", messageId, chatId);
        } catch (Exception e) {
            log.error("Failed to delete message {} for user {}: {}", messageId, chatId, safe(e));
        }
    }
}
