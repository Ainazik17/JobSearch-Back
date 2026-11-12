package job.search.kg.repo;

import job.search.kg.entity.CompanyFollower;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CompanyFollowerRepository extends JpaRepository<CompanyFollower, Long> {

    Optional<CompanyFollower> findByCompanyIdAndUserId(Long companyId, Long userId);

    @Query("SELECT f.company.id FROM CompanyFollower f WHERE f.user.id = :userId")
    List<Long> findCompanyIdsByUserId(@Param("userId") Long userId);

    @Query("SELECT f FROM CompanyFollower f JOIN FETCH f.user WHERE f.company.id = :companyId")
    List<CompanyFollower> findByCompanyIdWithUser(@Param("companyId") Long companyId);
}
