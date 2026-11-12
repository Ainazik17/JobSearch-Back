package job.search.kg.repo;

import job.search.kg.entity.VacancyResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Отклики (таблица vacancy_responses) с подгрузкой связей для экранов Mini App */
@Repository
public interface ApplicationQueries extends JpaRepository<VacancyResponse, Long> {

    @Query("""
        SELECT r FROM VacancyResponse r JOIN FETCH r.vacancy v JOIN FETCH v.user
        JOIN FETCH r.resume res JOIN FETCH res.user JOIN FETCH r.applicant
        WHERE r.id = :id
    """)
    Optional<VacancyResponse> findFull(@Param("id") Long id);

    @Query("""
        SELECT r FROM VacancyResponse r JOIN FETCH r.vacancy v JOIN FETCH r.resume res JOIN FETCH res.user
        WHERE v.id = :vacancyId ORDER BY r.createdAt DESC
    """)
    List<VacancyResponse> findByVacancy(@Param("vacancyId") Long vacancyId);

    @Query("""
        SELECT r FROM VacancyResponse r JOIN FETCH r.vacancy v JOIN FETCH v.user JOIN FETCH r.resume
        WHERE r.applicant.id = :userId ORDER BY r.createdAt DESC
    """)
    List<VacancyResponse> findByApplicant(@Param("userId") Long userId);

    /** [vacancyId, всего, новых] по вакансиям владельца */
    @Query("""
        SELECT r.vacancy.id, COUNT(r), SUM(CASE WHEN r.status = 'PENDING' THEN 1 ELSE 0 END)
        FROM VacancyResponse r WHERE r.vacancy.user.id = :ownerId GROUP BY r.vacancy.id
    """)
    List<Object[]> countsForOwner(@Param("ownerId") Long ownerId);

    boolean existsByVacancyIdAndApplicantId(Long vacancyId, Long applicantId);
}
