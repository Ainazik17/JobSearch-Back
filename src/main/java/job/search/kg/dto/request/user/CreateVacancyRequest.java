package job.search.kg.dto.request.user;
import jakarta.validation.constraints.NotNull;
import job.search.kg.entity.Vacancy;
import lombok.Data;

@Data
public class CreateVacancyRequest {
    private String title;
    private String description;
    private String salary;
    private String companyName;
    private String phone; // Опционально, если не указан - берётся из User
    private Integer cityId;
    private Integer sphereId;
    private Integer categoryId;
    private Integer subcategoryId;
    @NotNull
    private Integer minAge;
    @NotNull
    private Integer maxAge;
    @NotNull
    private Vacancy.GenderPreference preferredGender;
    private String address;
    private String schedule;
    @NotNull
    private Integer experienceInYear;
    private Double latitude;
    private Double longitude;


    /** Голосовое: ключ из POST /api/bot/voice (null — не менять, "" — удалить) */
    @jakarta.validation.constraints.Size(max = 500)
    private String voiceKey;
    @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(120)
    private Integer voiceDuration;
}
