package job.search.kg.repo;

import job.search.kg.entity.Deal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DealRepository extends JpaRepository<Deal, Long> {

    String FULL = "SELECT d FROM Deal d JOIN FETCH d.worker JOIN FETCH d.employer LEFT JOIN FETCH d.vacancy LEFT JOIN FETCH d.gig ";

    @Query(FULL + "WHERE d.id = :id")
    Optional<Deal> findFull(@Param("id") Long id);

    @Query(FULL + "WHERE d.worker.id = :userId ORDER BY d.createdAt DESC")
    List<Deal> findForWorker(@Param("userId") Long userId);

    @Query(FULL + "WHERE d.employer.id = :userId ORDER BY d.createdAt DESC")
    List<Deal> findForEmployer(@Param("userId") Long userId);

    Optional<Deal> findByWorkerIdAndVacancyId(Long workerId, Long vacancyId);

    @Query(FULL + "WHERE d.vacancy.id = :vacancyId AND d.status <> 'DECLINED'")
    List<Deal> findLiveByVacancy(@Param("vacancyId") Long vacancyId);

    /** Автоподтверждение: вторая сторона молчит дольше срока */
    @Query(FULL + "WHERE d.status IN ('AWAIT_WORKER', 'AWAIT_EMPLOYER') AND d.dueAt < :now")
    List<Deal> findOverdue(@Param("now") LocalDateTime now);

    /** Вопросы «Работали?», которые пора отправить в бот */
    @Query(FULL + "WHERE d.status = 'ASK_WORKER' AND d.askAfter <= :now AND d.notifiedAt IS NULL")
    List<Deal> findQuestionsToSend(@Param("now") LocalDateTime now);

    /** Рейтинг исполнителя: [userId, avg(employer_stars), подтверждённых работ] */
    @Query("""
        SELECT d.worker.id, AVG(d.employerStars), COUNT(d) FROM Deal d
        WHERE d.worker.id IN :ids AND d.status IN ('CONFIRMED', 'PROOF_REVIEW') GROUP BY d.worker.id
    """)
    List<Object[]> workerStats(@Param("ids") Collection<Long> ids);

    /** «Подтверждает отметки сотрудников»: [подтверждено, оспорено] по сделкам, начатым исполнителем */
    @Query("""
        SELECT SUM(CASE WHEN d.status = 'CONFIRMED' THEN 1 ELSE 0 END),
               SUM(CASE WHEN d.status IN ('DISPUTED', 'PROOF_REVIEW') THEN 1 ELSE 0 END)
        FROM Deal d WHERE d.employer.id = :employerId AND d.source = 'INVITE'
    """)
    List<Object[]> confirmStats(@Param("employerId") Long employerId);
}
