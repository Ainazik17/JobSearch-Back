package job.search.kg.controller.user;

import job.search.kg.dto.response.VacancyResponse;
import job.search.kg.service.user.BotSavedService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class BotSavedController {

    private final BotSavedService service;

    @GetMapping("/api/bot/saved")
    public List<VacancyResponse> list(@RequestParam Long telegramId) {
        return service.list(telegramId);
    }

    @GetMapping("/api/bot/saved/ids")
    public List<Long> ids(@RequestParam Long telegramId) {
        return service.ids(telegramId);
    }

    @PostMapping("/api/bot/saved/{vacancyId}")
    public ResponseEntity<Void> save(@PathVariable Long vacancyId, @RequestParam Long telegramId) {
        service.save(telegramId, vacancyId, true);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/bot/saved/{vacancyId}")
    public ResponseEntity<Void> unsave(@PathVariable Long vacancyId, @RequestParam Long telegramId) {
        service.save(telegramId, vacancyId, false);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/bot/notify-settings")
    public Map<String, Boolean> notifySettings(@RequestParam Long telegramId) {
        return service.notifySettings(telegramId);
    }

    @PutMapping("/api/bot/notify-settings")
    public Map<String, Boolean> updateNotify(@RequestParam Long telegramId, @RequestBody Map<String, Boolean> patch) {
        return service.updateNotify(telegramId, patch);
    }
}
