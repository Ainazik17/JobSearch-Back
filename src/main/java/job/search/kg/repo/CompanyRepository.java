package job.search.kg.repo;

import job.search.kg.entity.Company;
import job.search.kg.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByUser(User user);

    @Query("SELECT c FROM Company c JOIN FETCH c.user LEFT JOIN FETCH c.city WHERE c.id = :id")
    Optional<Company> findByIdWithUser(@Param("id") Long id);

    @Query("SELECT c FROM Company c JOIN FETCH c.user u WHERE u.isBanned = false OR u.isBanned IS NULL")
    List<Company> findAllVisible();

    @Query("SELECT c FROM Company c JOIN FETCH c.user u WHERE u.id = :userId")
    Optional<Company> findByUserId(@Param("userId") Long userId);

    /** [userId, число активных вакансий] — вакансии компании = активные вакансии владельца */
    @Query("SELECT v.user.id, COUNT(v) FROM Vacancy v WHERE v.isActive = true GROUP BY v.user.id")
    List<Object[]> countActiveVacanciesByUser();
}
