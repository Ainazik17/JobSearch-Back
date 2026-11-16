package job.search.kg.dto.request.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpsertSeriesRequest {
    @NotBlank(message = "Укажите название")
    @Size(max = 255)
    private String title;
    @Size(max = 5000)
    private String description;
    /** Ключ в MinIO или внешняя ссылка; обычно ставится загрузкой POST /{id}/cover */
    @Size(max = 500)
    private String coverUrl;
    private Integer sortOrder;
    private Boolean isActive;
}
