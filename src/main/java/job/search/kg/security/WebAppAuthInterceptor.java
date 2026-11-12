package job.search.kg.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Кто вызывает /api/bot/**: пользователь Mini App (подписанный initData в X-Telegram-Init-Data)
 * или сервис бота (X-Bot-Secret). Если в запросе указан telegramId (путь, параметр или X-Telegram-Id),
 * он должен совпадать с пользователем из подписи — иначе любой мог бы действовать от чужого имени.
 *
 * Режим telegram.webapp.auth.enforce=false (по умолчанию): нарушения только пишутся в лог,
 * чтобы сначала обновить бот и убедиться, что легитимные запросы проходят. true — блокируем (401/403).
 */
@Slf4j
@Component
public class WebAppAuthInterceptor implements HandlerInterceptor {

    public static final String VERIFIED_ATTR = "verifiedTelegramId";

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.webapp.auth.enforce:false}")
    private boolean enforce;

    @Value("${telegram.bot.service-secret:}")
    private String serviceSecret;

    /** initData живёт, пока открыт Mini App; сутки — с запасом */
    @Value("${telegram.webapp.auth.max-age-seconds:86400}")
    private long maxAge;

    @Override
    @SuppressWarnings("unchecked")
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) return true;

        Set<String> claimed = new LinkedHashSet<>();
        Object vars = req.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (vars instanceof Map<?, ?> m && m.get("telegramId") != null) claimed.add(String.valueOf(m.get("telegramId")));
        if (req.getParameter("telegramId") != null) claimed.add(req.getParameter("telegramId"));
        if (req.getHeader("X-Telegram-Id") != null) claimed.add(req.getHeader("X-Telegram-Id"));
        claimed.removeIf(String::isBlank);

        if (isService(req)) return true;

        Optional<Long> verified = TelegramInitData.verify(req.getHeader("X-Telegram-Init-Data"), botToken, maxAge);
        verified.ifPresent(id -> req.setAttribute(VERIFIED_ATTR, id));
        if (claimed.isEmpty()) return true; // публичные списки без пользователя

        String problem = null;
        if (verified.isEmpty()) {
            problem = "no valid initData";
        } else {
            String me = String.valueOf(verified.get());
            if (!claimed.stream().allMatch(me::equals)) problem = "telegramId " + claimed + " != signed " + me;
        }
        if (problem == null) return true;

        if (!enforce) {
            log.warn("[auth-dry-run] {} {} — {}", req.getMethod(), req.getRequestURI(), problem);
            return true;
        }
        log.warn("[auth] blocked {} {} — {}", req.getMethod(), req.getRequestURI(), problem);
        res.setStatus(verified.isEmpty() ? 401 : 403);
        res.setContentType("application/json;charset=UTF-8");
        res.getOutputStream().write(("{\"status\":" + res.getStatus()
                + ",\"message\":\"Откройте приложение заново из Telegram\"}").getBytes(StandardCharsets.UTF_8));
        return false;
    }

    private boolean isService(HttpServletRequest req) {
        String s = req.getHeader("X-Bot-Secret");
        return serviceSecret != null && !serviceSecret.isBlank() && s != null
                && MessageDigest.isEqual(serviceSecret.getBytes(StandardCharsets.UTF_8), s.getBytes(StandardCharsets.UTF_8));
    }
}
