package job.search.kg.controller.admin;

import jakarta.validation.Valid;
import job.search.kg.dto.request.admin.UpsertEpisodeRequest;
import job.search.kg.dto.request.admin.UpsertSeriesRequest;
import job.search.kg.dto.response.SeriesResponse;
import job.search.kg.service.admin.AdminSeriesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Раздел «Сериал»: серия № 1 бесплатна, остальные — с любым активным тарифом */
@RestController
@RequestMapping("/api/admin/series")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSeriesController {

    private final AdminSeriesService service;

    @GetMapping
    public List<SeriesResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public SeriesResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<SeriesResponse> create(@Valid @RequestBody UpsertSeriesRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req));
    }

    @PutMapping("/{id}")
    public SeriesResponse update(@PathVariable Long id, @Valid @RequestBody UpsertSeriesRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/cover", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SeriesResponse cover(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        return service.uploadCover(id, file);
    }

    @PostMapping("/{id}/episodes")
    public ResponseEntity<SeriesResponse.Episode> addEpisode(@PathVariable Long id,
                                                             @Valid @RequestBody UpsertEpisodeRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addEpisode(id, req));
    }

    @PutMapping("/episodes/{episodeId}")
    public SeriesResponse.Episode updateEpisode(@PathVariable Long episodeId,
                                                @Valid @RequestBody UpsertEpisodeRequest req) {
        return service.updateEpisode(episodeId, req);
    }

    @DeleteMapping("/episodes/{episodeId}")
    public ResponseEntity<Void> deleteEpisode(@PathVariable Long episodeId) {
        service.deleteEpisode(episodeId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/episodes/{episodeId}/thumbnail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SeriesResponse.Episode thumbnail(@PathVariable Long episodeId, @RequestParam("file") MultipartFile file) {
        return service.uploadThumbnail(episodeId, file);
    }
}
