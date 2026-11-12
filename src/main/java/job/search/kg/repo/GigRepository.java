package job.search.kg.repo;

import job.search.kg.entity.Gig;
import job.search.kg.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface GigRepository extends JpaRepository<Gig, Long> {

    /** Лента «Сегодня и завтра»: активные задания с датой не раньше from, сначала срочные, потом по дате */
    @Query("""
        SELECT g FROM Gig g JOIN FETCH g.user u JOIN FETCH g.city
        WHERE g.isActive = true AND g.workDate >= :from AND g.workDate <= :to
          AND (u.isBanned = false OR u.isBanned IS NULL)
          AND (:cityId IS NULL OR g.city.id = :cityId)
        ORDER BY g.urgent DESC, g.workDate ASC, g.createdAt DESC
    """)
    List<Gig> findFeed(@Param("from") LocalDate from, @Param("to") LocalDate to, @Param("cityId") Integer cityId);

    @Query("SELECT g FROM Gig g JOIN FETCH g.user JOIN FETCH g.city WHERE g.id = :id")
    Optional<Gig> findByIdWithUser(@Param("id") Long id);

    List<Gig> findByUserOrderByCreatedAtDesc(User user);

    /** Все задания за период (для «Надёжных заказчиков») */
    @Query("SELECT g FROM Gig g JOIN FETCH g.user u JOIN FETCH g.city WHERE g.createdAt >= :since AND (u.isBanned = false OR u.isBanned IS NULL)")
    List<Gig> findSince(@Param("since") java.time.LocalDateTime since);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Gig g SET g.isActive = false WHERE g.isActive = true AND g.workDate < :today")
    int deactivateBefore(@Param("today") LocalDate today);
}
