package job.search.kg.controller.user;

import jakarta.validation.Valid;
import job.search.kg.dto.request.user.UpsertCompanyRequest;
import job.search.kg.dto.response.CompanyResponse;
import job.search.kg.dto.response.CompanyReviewResponse;
import job.search.kg.dto.response.VacancyResponse;
import job.search.kg.service.user.BotCompanyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Компании (DESIGN_SPEC 4, 5) */
@RestController
@RequestMapping("/api/bot/companies")
@RequiredArgsConstructor
public class BotCompanyController {

    private final BotCompanyService companyService;

    @GetMapping
    public List<CompanyResponse> list(@RequestParam(required = false) Long telegramId,
                                      @RequestParam(required = false) String q,
                                      @RequestParam(required = false) String sphere) {
        return companyService.list(telegramId, q, sphere);
    }

    @GetMapping("/{id}")
    public CompanyResponse get(@PathVariable Long id, @RequestParam(required = false) Long telegramId) {
        return companyService.get(id, telegramId);
    }

    @GetMapping("/{id}/vacancies")
    public List<VacancyResponse> vacancies(@PathVariable Long id, @RequestParam(required = false) Long telegramId) {
        return companyService.vacancies(id, telegramId);
    }

    @GetMapping("/{id}/reviews")
    public List<CompanyReviewResponse> reviews(@PathVariable Long id) {
        return companyService.reviews(id);
    }

    @GetMapping("/by-vacancy/{vacancyId}")
    public ResponseEntity<CompanyResponse> byVacancy(@PathVariable Long vacancyId) {
        CompanyResponse r = companyService.byVacancy(vacancyId);
        return r == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(r);
    }

    @GetMapping("/user/{telegramId}")
    public ResponseEntity<CompanyResponse> mine(@PathVariable Long telegramId) {
        CompanyResponse r = companyService.mine(telegramId);
        return r == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(r);
    }

    @GetMapping("/following/{telegramId}")
    public List<CompanyResponse> following(@PathVariable Long telegramId) {
        return companyService.following(telegramId);
    }

    @PutMapping
    public CompanyResponse upsert(@RequestParam Long telegramId, @Valid @RequestBody UpsertCompanyRequest request) {
        return companyService.upsert(telegramId, request);
    }

    @PostMapping("/{id}/follow")
    public ResponseEntity<Void> follow(@PathVariable Long id, @RequestParam Long telegramId) {
        companyService.follow(id, telegramId, true);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/follow")
    public ResponseEntity<Void> unfollow(@PathVariable Long id, @RequestParam Long telegramId) {
        companyService.follow(id, telegramId, false);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CompanyResponse addPhoto(@RequestParam Long telegramId,
                                    @RequestParam("file") MultipartFile file,
                                    @RequestParam(required = false) String caption,
                                    @RequestParam(defaultValue = "false") boolean cover) throws Exception {
        return companyService.addPhoto(telegramId, file, caption, cover);
    }

    @DeleteMapping("/photos/{photoId}")
    public ResponseEntity<Void> deletePhoto(@PathVariable Long photoId, @RequestParam Long telegramId) {
        companyService.deletePhoto(telegramId, photoId);
        return ResponseEntity.noContent().build();
    }
}
