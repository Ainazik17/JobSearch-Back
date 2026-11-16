package job.search.kg.dto.request.admin;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BroadcastRequest {
    @Size(max = 16)
    private String emoji;       // 🚀
    @Size(max = 200)
    private String title;       // Важное обновление!
    @Size(max = 300)
    private String subtitle;    // От создателя этого бота
    @Size(max = 3500)
    private String message;     // тело сообщения
    @Size(max = 64)
    private String buttonText;  // Подписаться → (опционально)
    @Size(max = 500)
    private String buttonUrl;   // https://instagram.com/... (опционально)
    /** Вместо ссылки — экран Mini App: series, pro, saved, gig_12, company_3 … (?open=…) */
    @Size(max = 64)
    private String buttonOpen;
    @Size(max = 300)
    private String footer;      // Спасибо, что ты с нами ❤️ (опционально)
    /** Кому: ALL (по умолчанию), PRO — с активным тарифом, FREE — без тарифа */
    private Audience audience = Audience.ALL;

    public enum Audience { ALL, PRO, FREE }
}
