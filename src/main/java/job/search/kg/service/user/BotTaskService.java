package job.search.kg.service.user;

import job.search.kg.dto.request.user.CompleteTaskRequest;
import job.search.kg.dto.response.user.TaskCheckResponse;
import job.search.kg.dto.response.user.TaskListResponse;
import job.search.kg.entity.PointsTransaction;
import job.search.kg.entity.SocialTask;
import job.search.kg.entity.User;
import job.search.kg.entity.UserTask;
import job.search.kg.telegram.TelegramService;
import job.search.kg.exceptions.ResourceNotFoundException;
import job.search.kg.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@lombok.extern.slf4j.Slf4j
@Service
@RequiredArgsConstructor
public class BotTaskService {

    private final SocialTaskRepository taskRepository;
    private final UserTaskRepository userTaskRepository;
    private final UserRepository userRepository;
    private final BotPointsService pointsService;
    private final TelegramService telegramService;

    @Transactional(readOnly = true)
    public TaskListResponse getAvailableTasks(Long telegramId) {
        User user = userRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));

        List<SocialTask> allTasks = taskRepository.findByIsActive(true);
        List<UserTask> completedTasks = userTaskRepository.findByUser(user);

        List<Long> completedTaskIds = completedTasks.stream()
                .map(ut -> ut.getTask().getId().longValue())
                .collect(Collectors.toList());

        List<SocialTask> availableTasks = allTasks.stream()
                .filter(task -> !completedTaskIds.contains(task.getId().longValue()))
                .collect(Collectors.toList());

        TaskListResponse response = new TaskListResponse();
        response.setAvailableTasks(availableTasks);
        response.setCompletedTasks(completedTasks.stream().map(ut -> {
            TaskListResponse.CompletedTask c = new TaskListResponse.CompletedTask();
            c.setTaskId(ut.getTask().getId());
            c.setTitle(ut.getTask().getTitle());
            c.setRewardGiven(ut.getRewardGiven());
            c.setCompletedAt(ut.getCompletedAt());
            return c;
        }).collect(Collectors.toList()));

        return response;
    }

    @Transactional
    public UserTask completeTask(Long telegramId, CompleteTaskRequest request) {
        User user = userRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));

        SocialTask task = taskRepository.findById(request.getTaskId())
                .orElseThrow(() -> new ResourceNotFoundException("Задание не найдено"));

        // Проверка, не выполнено ли уже
        if (userTaskRepository.existsByUserAndTask(user, task)) {
            throw new IllegalStateException("Задание уже выполнено");
        }

        // Проверка подписки для Telegram
        if (task.getType() == SocialTask.TaskType.TELEGRAM) {
            if (telegramService.checkMembership(telegramId, channelOf(task)) != TelegramService.Membership.MEMBER) {
                throw new IllegalArgumentException("Вы не подписаны на канал");
            }
        }

        // Создание записи о выполнении
        UserTask userTask = new UserTask();
        userTask.setUser(user);
        userTask.setTask(task);
        userTask.setRewardGiven(task.getReward());
        userTaskRepository.save(userTask);

        // Начисление баллов
        pointsService.addPoints(
                telegramId,
                task.getReward(),
                PointsTransaction.TransactionType.TASK,
                "Задание: " + task.getTitle()
        );

        return userTask;
    }

    /**
     * Кнопка «Проверить» в Mini App: никогда не бросает исключение из-за подписки —
     * возвращает понятный статус, чтобы фронт показал верный текст вместо «Ошибка проверки».
     */
    @Transactional
    public TaskCheckResponse checkTask(Long telegramId, Integer taskId) {
        User user = userRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));
        SocialTask task = taskRepository.findById(taskId)
                .filter(t -> Boolean.TRUE.equals(t.getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Задание не найдено"));

        if (userTaskRepository.existsByUserAndTask(user, task)) {
            return new TaskCheckResponse(TaskCheckResponse.Status.ALREADY_DONE, true, 0, "Задание уже выполнено");
        }
        if (task.getType() == SocialTask.TaskType.TELEGRAM) {
            String channel = channelOf(task);
            if (channel == null) {
                log.error("Social task {} has no channel_id and link {} is not a t.me link", task.getId(), task.getLink());
                return new TaskCheckResponse(TaskCheckResponse.Status.CHECK_FAILED, false, 0,
                        "Не удалось проверить подписку. Попробуйте позже");
            }
            switch (telegramService.checkMembership(telegramId, channel)) {
                case NOT_MEMBER:
                    return new TaskCheckResponse(TaskCheckResponse.Status.NOT_SUBSCRIBED, false, 0,
                            "Сначала подпишитесь на канал");
                case ERROR:
                    return new TaskCheckResponse(TaskCheckResponse.Status.CHECK_FAILED, false, 0,
                            "Не удалось проверить подписку. Попробуйте через минуту");
                default:
                    break;
            }
        }

        UserTask userTask = new UserTask();
        userTask.setUser(user);
        userTask.setTask(task);
        userTask.setRewardGiven(task.getReward());
        userTaskRepository.save(userTask);
        pointsService.addPoints(telegramId, task.getReward(), PointsTransaction.TransactionType.TASK,
                "Задание: " + task.getTitle());
        return new TaskCheckResponse(TaskCheckResponse.Status.DONE, true, task.getReward(),
                "Начислено " + task.getReward() + " баллов");
    }

    /** channel_id (@name или -100…), иначе берём @name из ссылки https://t.me/name */
    static String channelOf(SocialTask task) {
        String c = task.getChannelId();
        if (c != null && !c.isBlank()) {
            c = c.trim();
            return c.startsWith("@") || c.startsWith("-") || c.matches("\\d+") ? c : "@" + c;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("t(?:elegram)?\\.me/([A-Za-z0-9_]{4,})").matcher(task.getLink() == null ? "" : task.getLink());
        return m.find() && !m.group(1).startsWith("joinchat") ? "@" + m.group(1) : null;
    }

    @Transactional(readOnly = true)
    public boolean isTaskCompleted(Long telegramId, Integer taskId) {
        User user = userRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));

        SocialTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Задание не найдено"));

        return userTaskRepository.existsByUserAndTask(user, task);
    }
}
