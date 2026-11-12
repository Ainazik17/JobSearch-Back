package job.search.kg.repo;

import job.search.kg.entity.SavedVacancy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SavedVacancyRepository extends JpaRepository<SavedVacancy, Long> {

    boolean existsByUserIdAndVacancyId(Long userId, Long vacancyId);

    @Modifying
    @Query("DELETE FROM SavedVacancy s WHERE s.user.id = :userId AND s.vacancy.id = :vacancyId")
    int deleteByUserAndVacancy(@Param("userId") Long userId, @Param("vacancyId") Long vacancyId);

    @Query("SELECT s.vacancy.id FROM SavedVacancy s WHERE s.user.id = :userId")
    List<Long> findVacancyIds(@Param("userId") Long userId);

    @Query("""
        SELECT s FROM SavedVacancy s JOIN FETCH s.vacancy v JOIN FETCH v.user JOIN FETCH v.city
        WHERE s.user.id = :userId ORDER BY s.createdAt DESC
    """)
    List<SavedVacancy> findWithVacancy(@Param("userId") Long userId);
}
