package job.search.kg.dto.response.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Результат кнопки «Проверить» у задания «Подпишитесь на канал» */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskCheckResponse {
    public enum Status { DONE, ALREADY_DONE, NOT_SUBSCRIBED, CHECK_FAILED }

    private Status status;
    private boolean success;
    /** Начислено баллов (0, если уже было выполнено) */
    private int earned;
    private String message;
}
