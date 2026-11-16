package job.search.kg.repo;

import job.search.kg.entity.SeriesEpisode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SeriesEpisodeRepository extends JpaRepository<SeriesEpisode, Long> {

    List<SeriesEpisode> findBySeriesIdOrderByNumberAsc(Long seriesId);

    List<SeriesEpisode> findBySeriesIdAndIsPublishedTrueOrderByNumberAsc(Long seriesId);

    long countBySeriesIdAndIsPublishedTrue(Long seriesId);

    boolean existsBySeriesIdAndNumber(Long seriesId, Integer number);

    @Query("SELECT e FROM SeriesEpisode e JOIN FETCH e.series WHERE e.id = :id")
    Optional<SeriesEpisode> findByIdWithSeries(@Param("id") Long id);

    @Query("SELECT COALESCE(MAX(e.number), 0) FROM SeriesEpisode e WHERE e.series.id = :seriesId")
    int maxNumber(@Param("seriesId") Long seriesId);
}
