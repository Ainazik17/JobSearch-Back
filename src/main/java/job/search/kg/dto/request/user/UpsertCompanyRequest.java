package job.search.kg.dto.request.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UpsertCompanyRequest {
    @NotBlank(message = "Укажите название компании")
    @Size(min = 2, max = 120, message = "Название — от 2 до 120 символов")
    private String name;

    @Size(max = 120, message = "Слишком длинное описание типа")
    private String type;

    @NotBlank(message = "Выберите сферу")
    @Size(max = 60)
    private String sphere;

    @Size(max = 140, message = "Цитата — до 140 символов")
    private String tagline;

    @Size(max = 3000, message = "Описание — до 3000 символов")
    private String about;

    @Size(max = 10, message = "Можно выбрать до 10 пунктов")
    private List<@Size(max = 50) String> perks;

    @Size(max = 20)
    private String staff;

    private Integer cityId;
}
