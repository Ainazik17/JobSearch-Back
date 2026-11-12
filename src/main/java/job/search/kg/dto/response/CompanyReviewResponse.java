package job.search.kg.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CompanyReviewResponse {
    private Long id;
    private String authorRole;
    private String tenure;
    private int stars;
    private String text;
    private boolean disputed;
}
