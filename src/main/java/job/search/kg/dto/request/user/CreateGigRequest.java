package job.search.kg.dto.request.user;

import jakarta.validation.constraints.*;
import job.search.kg.entity.Gig;
import lombok.Data;

import java.time.LocalDate;

/** Форма «Разовое задание» (DESIGN_SPEC 6.4) */
@Data
public class CreateGigRequest {
    /** С голосовым достаточно короткого заголовка (≥ 3), без него — ≥ 10 (проверка в сервисе) */
    @NotBlank(message = "Опишите задание — минимум 10 символов")
    @Size(min = 3, max = 255, message = "Опишите задание — минимум 10 символов")
    private String title;

    @NotNull(message = "Выберите дату")
    private LocalDate workDate;

    @Size(max = 120)
    private String timeText;

    @NotNull(message = "Укажите оплату")
    @Min(value = 1, message = "Укажите оплату")
    private Integer pay;

    private Gig.PayUnit payUnit = Gig.PayUnit.TOTAL;

    @Pattern(regexp = "^(1|2|3|4|5\\+)$", message = "Выберите вариант")
    private String peopleCount = "1";

    @NotBlank(message = "Укажите адрес")
    @Size(min = 3, max = 255, message = "Укажите адрес")
    private String address;

    private Double latitude;
    private Double longitude;

    private Boolean urgent = false;

    /** По умолчанию — город 1 (Бишкек), как и в остальных формах */
    private Integer cityId = 1;

    /** Голосовое: ключ из POST /api/bot/voice (null — не менять, "" — удалить) */
    @Size(max = 500)
    private String voiceKey;
    @Min(1) @Max(120)
    private Integer voiceDuration;
}
