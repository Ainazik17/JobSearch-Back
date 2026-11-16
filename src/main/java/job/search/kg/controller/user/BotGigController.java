package job.search.kg.controller.user;

import jakarta.validation.Valid;
import job.search.kg.dto.request.user.CreateGigRequest;
import job.search.kg.dto.response.GigResponse;
import job.search.kg.service.user.BotGigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Разовые задания (подработка) */
@RestController
@RequestMapping("/api/bot/gigs")
@RequiredArgsConstructor
public class BotGigController {

    private final BotGigService gigService;

    @PostMapping
    public ResponseEntity<GigResponse> create(@RequestParam Long telegramId,
                                              @Valid @RequestBody CreateGigRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(gigService.create(telegramId, request));
    }

    /** Лента «Сегодня и завтра» */
    @GetMapping
    public List<GigResponse> feed(@RequestParam Long telegramId,
                                  @RequestParam(required = false) Integer cityId,
                                  @RequestParam(required = false) Double lat,
                                  @RequestParam(required = false) Double lon) {
        return gigService.feed(telegramId, cityId, lat, lon);
    }

    /** «Надёжные заказчики» */
    @GetMapping("/top-customers")
    public List<job.search.kg.dto.response.GigCustomerResponse> topCustomers(
            @RequestParam(required = false) Long telegramId,
            @RequestParam(defaultValue = "10") int limit) {
        return gigService.topCustomers(telegramId, Math.min(Math.max(limit, 1), 20));
    }

    @GetMapping("/user/{telegramId}")
    public List<GigResponse> mine(@PathVariable Long telegramId) {
        return gigService.mine(telegramId);
    }

    @GetMapping("/{id}")
    public GigResponse get(@PathVariable Long id, @RequestParam Long telegramId) {
        return gigService.get(id, telegramId);
    }

    /** Написать заказчику через бота (у него нет username в Telegram) */
    @PostMapping("/{id}/contact")
    public java.util.Map<String, Boolean> contact(@PathVariable Long id, @RequestParam Long telegramId) {
        return java.util.Map.of("sent", gigService.contactAuthor(id, telegramId));
    }

    @PutMapping("/{id}/status")
    public GigResponse setStatus(@PathVariable Long id, @RequestParam Long telegramId,
                                 @RequestParam Boolean isActive) {
        return gigService.setActive(id, telegramId, isActive);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @RequestParam Long telegramId) {
        gigService.delete(id, telegramId);
        return ResponseEntity.noContent().build();
    }
}
