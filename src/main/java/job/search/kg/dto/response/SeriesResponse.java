package job.search.kg.dto.response;

import lombok.Data;

import java.util.List;

/** Сериал для Mini App. videoUrl у закрытых серий не отдаём */
@Data
public class SeriesResponse {
    private Long id;
    private String title;
    private String description;
    private String coverUrl;
    private int episodesCount;
    /** Сколько серий открыто без тарифа — для текста «2 серии бесплатно» */
    private int freeEpisodesCount;
    /** Есть активный тариф — открыты все серии */
    private boolean hasAccess;
    private Boolean isActive;
    private Integer sortOrder;
    /** Только в карточке сериала (в списке — null) */
    private List<Episode> episodes;

    @Data
    public static class Episode {
        private Long id;
        private Integer number;
        private String title;
        private String description;
        private String thumbnailUrl;
        private Integer durationSec;
        /** Бесплатная серия (отмечает админ) */
        private boolean free;
        /** Закрыта для этого зрителя: нужен тариф */
        private boolean locked;
        /** Ссылка на видео (YouTube), только если !locked */
        private String videoUrl;
        /** id видео YouTube для встроенного плеера, только если !locked */
        private String youtubeId;
        private Boolean isPublished;
    }
}
