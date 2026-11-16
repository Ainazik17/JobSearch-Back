package job.search.kg.dto.request.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpsertEpisodeRequest {
    /** Номер серии; пусто — следующий по порядку */
    @Min(1)
    private Integer number;
    @NotBlank(message = "Укажите название серии")
    @Size(max = 255)
    private String title;
    @Size(max = 5000)
    private String description;
    /** Ссылка на видео YouTube (лучше «Доступ по ссылке»): youtube.com/watch?v=…, youtu.be/…, shorts/… */
    @Size(max = 500)
    private String videoUrl;
    @Size(max = 500)
    private String thumbnailUrl;
    @Min(1)
    private Integer durationSec;
    /** Бесплатная — смотреть без тарифа. Не указано при создании: бесплатна только серия № 1 */
    private Boolean isFree;
    private Boolean isPublished;
}
