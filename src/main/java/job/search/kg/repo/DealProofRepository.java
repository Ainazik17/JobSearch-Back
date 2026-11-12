package job.search.kg.repo;

import job.search.kg.entity.DealProof;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DealProofRepository extends JpaRepository<DealProof, Long> {
}
