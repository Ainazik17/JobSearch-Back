package job.search.kg.service.admin;

import job.search.kg.dto.request.admin.UpsertEpisodeRequest;
import job.search.kg.dto.request.admin.UpsertSeriesRequest;
import job.search.kg.dto.response.SeriesResponse;
import job.search.kg.entity.Series;
import job.search.kg.entity.SeriesEpisode;
import job.search.kg.exceptions.ConflictException;
import job.search.kg.exceptions.ResourceNotFoundException;
import job.search.kg.repo.SeriesEpisodeRepository;
import job.search.kg.repo.SeriesRepository;
import job.search.kg.service.MinioStorageService;
import job.search.kg.util.YouTube;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Админка раздела «Сериал»: сериалы, серии (ссылки YouTube), обложки и превью */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminSeriesService {

    private final SeriesRepository seriesRepository;
    private final SeriesEpisodeRepository episodeRepository;
    private final MinioStorageService storage;

    @Transactional(readOnly = true)
    public List<SeriesResponse> list() {
        return seriesRepository.findAllByOrderBySortOrderAscIdDesc().stream().map(s -> toResponse(s, false)).toList();
    }

    @Transactional(readOnly = true)
    public SeriesResponse get(Long id) {
        return toResponse(series(id), true);
    }

    @Transactional
    public SeriesResponse create(UpsertSeriesRequest req) {
        Series s = new Series();
        apply(s, req);
        return toResponse(seriesRepository.save(s), true);
    }

    @Transactional
    public SeriesResponse update(Long id, UpsertSeriesRequest req) {
        Series s = series(id);
        apply(s, req);
        return toResponse(seriesRepository.save(s), true);
    }

    @Transactional
    public void delete(Long id) {
        seriesRepository.delete(series(id)); // серии удалятся каскадом в БД
    }

    @Transactional
    public SeriesResponse uploadCover(Long id, MultipartFile file) {
        requireType(file, "image/");
        Series s = series(id);
        s.setCoverUrl(upload(file, id));
        return toResponse(seriesRepository.save(s), true);
    }

    @Transactional
    public SeriesResponse.Episode addEpisode(Long seriesId, UpsertEpisodeRequest req) {
        Series s = series(seriesId);
        SeriesEpisode e = new SeriesEpisode();
        e.setSeries(s);
        int number = req.getNumber() != null ? req.getNumber() : episodeRepository.maxNumber(seriesId) + 1;
        if (episodeRepository.existsBySeriesIdAndNumber(seriesId, number)) {
            throw new ConflictException("Серия № " + number + " уже есть");
        }
        e.setNumber(number);
        applyEpisode(e, req);
        return toEpisode(episodeRepository.save(e));
    }

    @Transactional
    public SeriesResponse.Episode updateEpisode(Long episodeId, UpsertEpisodeRequest req) {
        SeriesEpisode e = episode(episodeId);
        if (req.getNumber() != null && !req.getNumber().equals(e.getNumber())) {
            if (episodeRepository.existsBySeriesIdAndNumber(e.getSeries().getId(), req.getNumber())) {
                throw new ConflictException("Серия № " + req.getNumber() + " уже есть");
            }
            e.setNumber(req.getNumber());
        }
        applyEpisode(e, req);
        return toEpisode(episodeRepository.save(e));
    }

    @Transactional
    public void deleteEpisode(Long episodeId) {
        episodeRepository.delete(episode(episodeId));
    }

    @Transactional
    public SeriesResponse.Episode uploadThumbnail(Long episodeId, MultipartFile file) {
        requireType(file, "image/");
        SeriesEpisode e = episode(episodeId);
        e.setThumbnailUrl(upload(file, e.getSeries().getId()));
        return toEpisode(episodeRepository.save(e));
    }

    private void apply(Series s, UpsertSeriesRequest req) {
        s.setTitle(req.getTitle().trim());
        s.setDescription(req.getDescription());
        if (req.getCoverUrl() != null) s.setCoverUrl(req.getCoverUrl().isBlank() ? null : req.getCoverUrl().trim());
        if (req.getSortOrder() != null) s.setSortOrder(req.getSortOrder());
        if (req.getIsActive() != null) s.setIsActive(req.getIsActive());
    }

    private void applyEpisode(SeriesEpisode e, UpsertEpisodeRequest req) {
        e.setTitle(req.getTitle().trim());
        e.setDescription(req.getDescription());
        if (req.getVideoUrl() != null) {
            String v = req.getVideoUrl().trim();
            if (!v.isEmpty() && YouTube.videoId(v) == null) {
                throw new IllegalArgumentException("Нужна ссылка на видео YouTube");
            }
            e.setVideoUrl(v.isEmpty() ? null : v);
        }
        if (req.getThumbnailUrl() != null) e.setThumbnailUrl(req.getThumbnailUrl().isBlank() ? null : req.getThumbnailUrl().trim());
        if (req.getDurationSec() != null) e.setDurationSec(req.getDurationSec());
        if (req.getIsFree() != null) e.setIsFree(req.getIsFree());
        if (req.getIsPublished() != null) e.setIsPublished(req.getIsPublished());
    }

    private String upload(MultipartFile file, Long seriesId) {
        try {
            return storage.uploadSeriesFile(file, seriesId);
        } catch (Exception ex) {
            log.error("Series upload failed", ex);
            throw new IllegalArgumentException("Не удалось загрузить файл");
        }
    }

    private static void requireType(MultipartFile file, String prefix) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Файл пустой");
        String type = file.getContentType();
        if (type == null || !type.startsWith(prefix)) {
            throw new IllegalArgumentException(prefix.startsWith("video") ? "Нужен видеофайл" : "Нужна картинка");
        }
    }

    private Series series(Long id) {
        return seriesRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Сериал не найден"));
    }

    private SeriesEpisode episode(Long id) {
        return episodeRepository.findByIdWithSeries(id).orElseThrow(() -> new ResourceNotFoundException("Серия не найдена"));
    }

    private SeriesResponse toResponse(Series s, boolean withEpisodes) {
        SeriesResponse r = new SeriesResponse();
        r.setId(s.getId());
        r.setTitle(s.getTitle());
        r.setDescription(s.getDescription());
        r.setCoverUrl(url(s.getCoverUrl()));
        r.setIsActive(s.getIsActive());
        r.setSortOrder(s.getSortOrder());
        r.setHasAccess(true);
        List<SeriesEpisode> eps = episodeRepository.findBySeriesIdOrderByNumberAsc(s.getId());
        r.setEpisodesCount(eps.size());
        if (withEpisodes) r.setEpisodes(eps.stream().map(this::toEpisode).toList());
        return r;
    }

    private SeriesResponse.Episode toEpisode(SeriesEpisode e) {
        SeriesResponse.Episode r = new SeriesResponse.Episode();
        r.setId(e.getId());
        r.setNumber(e.getNumber());
        r.setTitle(e.getTitle());
        r.setDescription(e.getDescription());
        String yt = YouTube.videoId(e.getVideoUrl());
        r.setThumbnailUrl(e.getThumbnailUrl() != null ? url(e.getThumbnailUrl()) : YouTube.thumbnail(yt));
        r.setYoutubeId(yt);
        r.setDurationSec(e.getDurationSec());
        r.setFree(e.isOpenForAll());
        r.setLocked(false);
        r.setVideoUrl(e.getVideoUrl());
        r.setIsPublished(e.getIsPublished());
        return r;
    }

    private String url(String v) {
        try {
            return storage.resolveUrl(v, 1);
        } catch (Exception ex) {
            return null;
        }
    }
}
