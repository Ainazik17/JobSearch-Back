package job.search.kg.repo;

import job.search.kg.entity.User;
import job.search.kg.entity.WorkerOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkerOfferRepository extends JpaRepository<WorkerOffer, Long> {

    Optional<WorkerOffer> findByUser(User user);

    @Query("SELECT o FROM WorkerOffer o JOIN FETCH o.user JOIN FETCH o.city WHERE o.id = :id")
    Optional<WorkerOffer> findByIdWithUser(@Param("id") Long id);

    @Query("""
        SELECT o FROM WorkerOffer o JOIN FETCH o.user u JOIN FETCH o.city
        WHERE o.isActive = true AND (:cityId IS NULL OR o.city.id = :cityId)
          AND (u.isBanned = false OR u.isBanned IS NULL)
        ORDER BY o.updatedAt DESC NULLS LAST, o.createdAt DESC
    """)
    List<WorkerOffer> findActive(@Param("cityId") Integer cityId);
}
