package job.search.kg.dto.request.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkPlaceDto {
    @NotBlank(message = "Укажите, где работали")
    @Size(max = 120)
    private String place;
    @Size(max = 120)
    private String role;
    @Size(max = 60)
    private String period;
}
