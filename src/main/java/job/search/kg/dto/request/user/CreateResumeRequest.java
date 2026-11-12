package job.search.kg.dto.request.user;

import job.search.kg.entity.Resume;
import lombok.Data;

@Data
public class CreateResumeRequest {
    private String name;
    private Integer age;
    private Resume.Gender gender;
    private Integer cityId;
    private Integer categoryId;
    private Integer subcategoryId;
    private Integer experience;
    private String description;
    private Boolean isActive = true;
    private String phone;
    /** «Где работали»: null — не менять, [] — очистить. До 5 записей. */
    @jakarta.validation.constraints.Size(max = 5, message = "Можно указать до 5 мест работы")
    private java.util.List<@jakarta.validation.Valid WorkPlaceDto> workPlaces;
}
