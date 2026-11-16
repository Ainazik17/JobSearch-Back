package job.search.kg.repo;

import job.search.kg.entity.Series;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeriesRepository extends JpaRepository<Series, Long> {
    List<Series> findByIsActiveTrueOrderBySortOrderAscIdDesc();

    List<Series> findAllByOrderBySortOrderAscIdDesc();
}
