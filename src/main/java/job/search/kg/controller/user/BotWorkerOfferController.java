package job.search.kg.controller.user;

import jakarta.validation.Valid;
import job.search.kg.dto.request.user.UpsertWorkerOfferRequest;
import job.search.kg.dto.response.WorkerOfferResponse;
import job.search.kg.service.user.BotWorkerOfferService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** «Мои услуги» — исполнители, готовые к подработке */
@RestController
@RequestMapping("/api/bot/offers")
@RequiredArgsConstructor
public class BotWorkerOfferController {

    private final BotWorkerOfferService offerService;

    /** Создать или обновить свою карточку */
    @PutMapping
    public WorkerOfferResponse upsert(@RequestParam Long telegramId,
                                      @Valid @RequestBody UpsertWorkerOfferRequest request) {
        return offerService.upsert(telegramId, request);
    }

    /** Своя карточка; 204, если ещё не создана */
    @GetMapping("/user/{telegramId}")
    public ResponseEntity<WorkerOfferResponse> mine(@PathVariable Long telegramId) {
        return offerService.mine(telegramId).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/user/{telegramId}/status")
    public WorkerOfferResponse setStatus(@PathVariable Long telegramId, @RequestParam Boolean isActive) {
        return offerService.setActive(telegramId, isActive);
    }

    @GetMapping
    public List<WorkerOfferResponse> list(@RequestParam Long telegramId,
                                          @RequestParam(required = false) Integer cityId) {
        return offerService.list(telegramId, cityId);
    }

    /** Написать исполнителю через бота (у него нет username в Telegram) */
    @PostMapping("/{id}/contact")
    public java.util.Map<String, Boolean> contact(@PathVariable Long id, @RequestParam Long telegramId) {
        return java.util.Map.of("sent", offerService.contactAuthor(id, telegramId));
    }

    @GetMapping("/{id}")
    public WorkerOfferResponse get(@PathVariable Long id, @RequestParam Long telegramId) {
        return offerService.get(id, telegramId);
    }
}
