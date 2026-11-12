package job.search.kg.service.user;

import job.search.kg.repo.GigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

/** Разовые задания с прошедшей датой снимаем автоматически — ленты и «Мои задания» не захламляются */
@Slf4j
@Component
@RequiredArgsConstructor
public class GigExpiryJob {

    private final GigRepository gigRepository;

    @Scheduled(cron = "0 5 * * * *", zone = "Asia/Bishkek")
    @Transactional
    public void closePastGigs() {
        int n = gigRepository.deactivateBefore(LocalDate.now(ZoneId.of("Asia/Bishkek")));
        if (n > 0) log.info("Closed {} past gigs", n);
    }
}
