package job.search.kg.controller.user;

import job.search.kg.dto.response.ApplicationResponse;
import job.search.kg.service.user.BotApplicationService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Отклики резюме на вакансии (Mini App) */
@RestController
@RequestMapping("/api/bot/applications")
@RequiredArgsConstructor
public class BotApplicationController {

    private final BotApplicationService service;

    @Data
    public static class ApplyRequest {
        private Long vacancyId;
        private Long resumeId;
        private String message;
    }

    @PostMapping
    public ResponseEntity<ApplicationResponse> apply(@RequestParam Long telegramId, @RequestBody ApplyRequest req) {
        if (req.getVacancyId() == null) throw new IllegalArgumentException("Не указана вакансия");
        if (req.getMessage() != null && req.getMessage().length() > 500) {
            throw new IllegalArgumentException("Сообщение — до 500 символов");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.apply(telegramId, req.getVacancyId(), req.getResumeId(), req.getMessage()));
    }

    @GetMapping("/mine")
    public List<ApplicationResponse> mine(@RequestParam Long telegramId) {
        return service.mine(telegramId);
    }

    @GetMapping("/vacancy/{vacancyId}")
    public List<ApplicationResponse> forVacancy(@PathVariable Long vacancyId, @RequestParam Long telegramId) {
        return service.forVacancy(telegramId, vacancyId);
    }

    @GetMapping("/counts")
    public Map<Long, Map<String, Long>> counts(@RequestParam Long telegramId) {
        return service.counts(telegramId);
    }

    @GetMapping("/{id}")
    public ApplicationResponse get(@PathVariable Long id, @RequestParam Long telegramId) {
        return service.get(telegramId, id);
    }

    @PutMapping("/{id}/status")
    public ApplicationResponse decide(@PathVariable Long id, @RequestParam Long telegramId, @RequestParam String status) {
        return service.decide(telegramId, id, status);
    }
}
