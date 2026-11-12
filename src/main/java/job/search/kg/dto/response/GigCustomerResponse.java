package job.search.kg.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** «Надёжные заказчики» на вкладке «Подработка» */
@Data
@Builder
public class GigCustomerResponse {
    private Long userId;
    private String name;          // компания или «Айгерим К.»
    private Long companyId;       // есть профиль компании → ведём в него
    private boolean verified;
    private String coverUrl;
    private String type;          // «Ресторан турецкой кухни» или «Частное лицо»
    private int gigsCount;        // заданий за 90 дней
    private Double rating;        // оценки исполнителей — со «Сделками»
    private List<GigResponse> openGigs;
}
