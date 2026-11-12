package job.search.kg.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** Карточка (список) и профиль компании. Поля профиля заполняются только в детальном ответе. */
@Data
@Builder
public class CompanyResponse {
    private Long id;
    private String name;
    private String type;
    private String sphere;
    private String cityName;
    private String tagline;
    private String coverUrl;
    private boolean verified;
    private Double rating;          // null — отзывов нет
    private long reviewsCount;
    private Integer paidOnTimePct;  // null — нет данных
    private Integer confirmRatePct; // null — появится со Сделками
    private long vacanciesCount;
    private boolean following;
    private boolean mine;

    // Профиль
    private String about;
    private List<String> perks;
    private String staff;
    private Integer since;
    private int hiredCount;
    private long followersCount;
    private List<Photo> photos;
    private Bars bars;

    @Data
    @Builder
    public static class Photo {
        private Long id;
        private String url;
        private String caption;
    }

    /** Три полосы во вкладке «Отзывы» (средние 1–5) */
    @Data
    @Builder
    public static class Bars {
        private Double paidOnTime;
        private Double conditions;
        private Double attitude;
    }
}
