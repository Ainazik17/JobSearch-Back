package job.search.kg.dto.response.user;

import job.search.kg.entity.SocialTask;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class TaskListResponse {
    private List<SocialTask> availableTasks;
    /** Плоский DTO: сущность UserTask с ленивыми user/task Jackson не сериализует */
    private List<CompletedTask> completedTasks;

    @Data
    public static class CompletedTask {
        private Integer taskId;
        private String title;
        private Integer rewardGiven;
        private LocalDateTime completedAt;
    }
}
