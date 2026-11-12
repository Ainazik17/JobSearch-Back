package job.search.kg.dto.response;

import job.search.kg.dto.request.user.WorkPlaceDto;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** Отклик: для работодателя — карточка кандидата, для соискателя — статус */
@Data
@Builder
public class ApplicationResponse {
    private Long id;
    /** true — смотрит владелец вакансии */
    private boolean forEmployer;
    /** NEW · VIEWED · INVITED · REJECTED */
    private String status;
    private String message;
    private LocalDateTime createdAt;

    private Long vacancyId;
    private String vacancyTitle;
    private String companyName;
    private String vacancySalary;
    /** Для соискателя после приглашения — контакты работодателя */
    private String employerPhone;
    private String employerUsername;

    private Long resumeId;
    private String name;
    private Integer age;
    private String gender;
    private String cityName;
    private String categoryName;
    private String subcategoryName;
    private Integer experience;
    private String description;
    private String photoUrl;
    private String phone;
    private String telegramUsername;
    private List<WorkPlaceDto> workPlaces;
    /** Рейтинг исполнителя по сделкам и число подтверждённых работ */
    private Double workerRating;
    private long confirmedWorks;
}
