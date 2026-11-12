package job.search.kg.repo;

import job.search.kg.entity.ResumeWorkPlace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResumeWorkPlaceRepository extends JpaRepository<ResumeWorkPlace, Long> {
    List<ResumeWorkPlace> findByResumeIdOrderByDisplayOrderAscIdAsc(Long resumeId);

    @Modifying
    @Query("DELETE FROM ResumeWorkPlace w WHERE w.resume.id = :resumeId")
    void deleteByResumeId(@Param("resumeId") Long resumeId);
}
