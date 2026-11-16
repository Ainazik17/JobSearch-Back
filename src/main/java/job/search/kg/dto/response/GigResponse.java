package job.search.kg.dto.response;

import job.search.kg.entity.Gig;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class GigResponse {
    private Long id;
    private String title;
    private LocalDate workDate;
    private String timeText;
    private Integer pay;
    private Gig.PayUnit payUnit;
    private String peopleCount;
    private String address;
    private Double latitude;
    private Double longitude;
    private Double distanceKm;
    private Boolean urgent;
    private Integer cityId;
    private String cityName;
    /** «Айгерим К.» */
    private String authorName;
    /** Без PRO — замаскирован («+99670 *** ***»), telegramUsername = null */
    private String phone;
    private String telegramUsername;
    private Boolean isActive;
    private Boolean mine;
    private LocalDateTime createdAt;
    /** Голосовое (presigned-ссылка) и длительность, сек */
    private String voiceUrl;
    private Integer voiceDuration;
    /** Контакты раскрыты (PRO или своё объявление). false — показываем «Открыть контакты · PRO» */
    private Boolean contactsOpen;
    /** Есть ли у автора WhatsApp и username (без раскрытия самих контактов).
     *  Нет username — кнопка Telegram шлёт автору сообщение через бота (POST .../{id}/contact) */
    private Boolean hasPhone;
    private Boolean hasTelegram;
}
