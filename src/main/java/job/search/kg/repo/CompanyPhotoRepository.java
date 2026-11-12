package job.search.kg.repo;

import job.search.kg.entity.CompanyPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CompanyPhotoRepository extends JpaRepository<CompanyPhoto, Long> {
    List<CompanyPhoto> findByCompanyIdOrderByDisplayOrderAscIdAsc(Long companyId);
    long countByCompanyId(Long companyId);
}
