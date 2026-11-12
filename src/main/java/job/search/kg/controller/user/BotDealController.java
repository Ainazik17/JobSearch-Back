package job.search.kg.controller.user;

import job.search.kg.dto.response.DealResponse;
import job.search.kg.service.user.BotDealService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/** Сделки (DESIGN_SPEC 7) */
@RestController
@RequestMapping("/api/bot/deals")
@RequiredArgsConstructor
public class BotDealController {

    private final BotDealService service;

    @GetMapping("/worker")
    public Map<String, Object> worker(@RequestParam Long telegramId) {
        return service.worker(telegramId);
    }

    @GetMapping("/employer")
    public Map<String, Object> employer(@RequestParam Long telegramId) {
        return service.employer(telegramId);
    }

    @GetMapping("/pending")
    public Map<String, Long> pending(@RequestParam Long telegramId) {
        return Map.of("count", service.pending(telegramId));
    }

    @GetMapping("/{id}")
    public DealResponse get(@PathVariable Long id, @RequestParam Long telegramId) {
        return service.get(telegramId, id);
    }

    /** Исполнитель: stars = «да» с оценкой, без stars = «нет, не работали» */
    @PostMapping("/{id}/answer")
    public DealResponse answer(@PathVariable Long id, @RequestParam Long telegramId,
                               @RequestParam(required = false) Integer stars) {
        return service.workerAnswer(telegramId, id, stars);
    }

    @PostMapping("/{id}/confirm")
    public DealResponse confirm(@PathVariable Long id, @RequestParam Long telegramId, @RequestParam boolean yes) {
        return service.workerConfirm(telegramId, id, yes);
    }

    @PostMapping("/{id}/rate")
    public DealResponse rate(@PathVariable Long id, @RequestParam Long telegramId, @RequestParam int stars) {
        return service.employerRate(telegramId, id, stars);
    }

    @PostMapping("/{id}/dispute")
    public DealResponse dispute(@PathVariable Long id, @RequestParam Long telegramId) {
        return service.employerDispute(telegramId, id);
    }

    @PostMapping(value = "/{id}/proof", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DealResponse proof(@PathVariable Long id, @RequestParam Long telegramId, @RequestParam String kind,
                              @RequestParam("file") MultipartFile file) throws Exception {
        return service.addProof(telegramId, id, kind, file);
    }

    @Data
    public static class ReviewRequest {
        private int a;
        private int b;
        private int c;
        private String text;
    }

    @PostMapping("/{id}/review")
    public DealResponse review(@PathVariable Long id, @RequestParam Long telegramId, @RequestBody ReviewRequest r) {
        return service.review(telegramId, id, r.getA(), r.getB(), r.getC(), r.getText());
    }

    @Data
    public static class CloseRequest {
        /** HIRED | NONE | OUTSIDE */
        private String result;
        private List<Long> workerIds;
    }

    @PostMapping("/vacancies/{vacancyId}/close")
    public DealResponse.VacancyRow close(@PathVariable Long vacancyId, @RequestParam Long telegramId,
                                         @RequestBody CloseRequest r) {
        return service.closeVacancy(telegramId, vacancyId, r.getResult(), r.getWorkerIds());
    }
}
