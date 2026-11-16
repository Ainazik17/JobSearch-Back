package job.search.kg.controller.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import job.search.kg.dto.request.admin.BroadcastRequest;
import job.search.kg.exceptions.ConflictException;
import job.search.kg.exceptions.ResourceNotFoundException;
import job.search.kg.service.admin.BroadcastService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/broadcast")
@Tag(name = "Admin Broadcast", description = "Рассылка сообщений пользователям бота")
@RequiredArgsConstructor
public class AdminBroadcastController {

    private final BroadcastService broadcastService;

    @Operation(summary = "Запустить рассылку (в фоне). Прогресс — GET /jobs/{id}")
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> send(@Valid @RequestBody BroadcastRequest request, Authentication auth) {
        try {
            return ResponseEntity.accepted().body(broadcastService.start(request, auth != null ? auth.getName() : null).view());
        } catch (IllegalStateException e) {
            throw new ConflictException(e.getMessage());
        }
    }

    @Operation(summary = "Тест: отправить сообщение одному пользователю (себе)")
    @PostMapping("/test")
    public Map<String, Object> test(@Valid @RequestBody BroadcastRequest request, @RequestParam Long telegramId) {
        return Map.of("sent", broadcastService.sendTest(request, telegramId));
    }

    @Operation(summary = "Сколько человек получит рассылку")
    @GetMapping("/recipients")
    public Map<String, Object> recipients(@RequestParam(defaultValue = "ALL") BroadcastRequest.Audience audience) {
        return Map.of("audience", audience, "count", broadcastService.countRecipients(audience));
    }

    @GetMapping("/jobs")
    public List<Map<String, Object>> jobs() {
        return broadcastService.recentJobs().stream().map(BroadcastService.Job::view).toList();
    }

    @GetMapping("/jobs/{id}")
    public Map<String, Object> job(@PathVariable String id) {
        return broadcastService.job(id).map(BroadcastService.Job::view)
                .orElseThrow(() -> new ResourceNotFoundException("Рассылка не найдена"));
    }

    @PostMapping("/jobs/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable String id) {
        return Map.of("cancelled", broadcastService.cancel(id));
    }
}
