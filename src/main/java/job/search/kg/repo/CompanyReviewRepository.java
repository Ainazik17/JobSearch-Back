package job.search.kg.repo;

import job.search.kg.entity.CompanyReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CompanyReviewRepository extends JpaRepository<CompanyReview, Long> {

    List<CompanyReview> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    java.util.Optional<CompanyReview> findByDealId(Long dealId);

    /** [companyId, count, avg(stars), share(paid_on_time >= 4), avg(paid), avg(conditions), avg(attitude)] */
    @Query("""
        SELECT r.company.id, COUNT(r), AVG(r.stars),
               AVG(CASE WHEN r.paidOnTime IS NULL THEN NULL WHEN r.paidOnTime >= 4 THEN 1.0 ELSE 0.0 END),
               AVG(r.paidOnTime), AVG(r.conditions), AVG(r.attitude)
        FROM CompanyReview r GROUP BY r.company.id
    """)
    List<Object[]> aggregateAll();
}
