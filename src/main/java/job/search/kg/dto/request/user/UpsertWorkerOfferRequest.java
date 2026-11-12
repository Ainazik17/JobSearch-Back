package job.search.kg.dto.request.user;

import jakarta.validation.constraints.*;
import job.search.kg.entity.WorkerOffer;
import lombok.Data;

import java.util.List;

/** Форма «Мои услуги» (DESIGN_SPEC 6.4b) */
@Data
public class UpsertWorkerOfferRequest {
    @NotEmpty(message = "Выберите, что умеете")
    @Size(max = 8, message = "Можно выбрать до 8")
    private List<@NotBlank @Size(max = 80) String> skills;

    @NotNull(message = "Укажите цену")
    @Min(value = 1, message = "Укажите цену")
    private Integer price;

    private WorkerOffer.PriceUnit priceUnit = WorkerOffer.PriceUnit.HOUR;

    @NotEmpty(message = "Выберите хотя бы один день")
    private List<@Pattern(regexp = "^(Пн|Вт|Ср|Чт|Пт|Сб|Вс)$") String> days;

    @Size(max = 255)
    private String district;

    @Size(max = 2000)
    private String about;

    private Integer cityId = 1;

    private Boolean isActive = true;
}
