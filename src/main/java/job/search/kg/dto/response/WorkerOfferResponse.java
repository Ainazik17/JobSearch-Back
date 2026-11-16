package job.search.kg.dto.response;

import job.search.kg.entity.WorkerOffer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class WorkerOfferResponse {
    private Long id;
    private List<String> skills;
    private Integer price;
    private WorkerOffer.PriceUnit priceUnit;
    private List<String> days;
    private String district;
    private String about;
    private Integer cityId;
    private String cityName;
    private String name;
    /** Без PRO — замаскирован, telegramUsername = null */
    private String phone;
    private String telegramUsername;
    private Boolean isActive;
    private Boolean mine;
    private LocalDateTime createdAt;
    /** Контакты раскрыты (PRO или своё объявление). false — показываем «Открыть контакты · PRO» */
    private Boolean contactsOpen;
    /** Есть ли у автора WhatsApp и username (без раскрытия самих контактов).
     *  Нет username — кнопка Telegram шлёт автору сообщение через бота (POST .../{id}/contact) */
    private Boolean hasPhone;
    private Boolean hasTelegram;
}
