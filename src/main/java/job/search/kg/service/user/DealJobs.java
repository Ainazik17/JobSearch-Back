package job.search.kg.service.user;

import job.search.kg.telegram.notification.DealNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Сделки по расписанию: вопросы «Работали?» в бот и автоподтверждение по истечении 48 ч */
@Slf4j
@Component
@RequiredArgsConstructor
public class DealJobs {

    private final BotDealService dealService;
    private final DealNotificationService notifications;

    @Scheduled(cron = "0 */15 * * * *", zone = "Asia/Bishkek")
    public void run() {
        try {
            dealService.dueQuestions().forEach(notifications::askWorker);
            int n = dealService.autoConfirm();
            if (n > 0) log.info("Auto-confirmed {} deals", n);
        } catch (Exception e) {
            log.error("Deal jobs failed: {}", e.getMessage(), e);
        }
    }
}
