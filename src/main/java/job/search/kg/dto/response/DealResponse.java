package job.search.kg.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** Сделка с точки зрения того, кто смотрит (исполнитель или работодатель) */
@Data
@Builder
public class DealResponse {
    private Long id;
    private String status;
    private String source;
    /** WORKER | EMPLOYER — моя роль в сделке */
    private String side;
    private String title;
    private String whenText;
    /** Вторая сторона: компания или «Айгерим К.» */
    private String otherName;
    private Long otherCompanyId;
    private boolean otherVerified;
    private Long vacancyId;
    private Long gigId;
    private Integer myStars;
    /** Оценку второй стороны видно только после своего ответа */
    private Integer otherStars;
    private LocalDateTime dueAt;
    /** Можно оставить отзыв (3 критерия + текст) */
    private boolean canReview;
    private boolean reviewed;
    private LocalDateTime createdAt;

    /** «Мои вакансии» у работодателя */
    @Data
    @Builder
    public static class VacancyRow {
        private Long vacancyId;
        private String title;
        private String schedule;
        private boolean active;
        private String closeResult;
        private String statusText;
        private List<Person> wrote;
        private List<String> hired;
    }

    @Data
    @Builder
    public static class Person {
        private Long userId;
        private Long applicationId;
        private String name;
        private Double rating;
        private long works;
    }

    @Data
    @Builder
    public static class Summary {
        private long confirmedWorks;
        private int badgeGoal;
        private boolean verifiedWorker;
        private long pending; // вопросы + запросы — для счётчика на табе
    }
}
