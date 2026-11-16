package job.search.kg.service.user;

import job.search.kg.dto.response.SeriesResponse;
import job.search.kg.entity.Series;
import job.search.kg.entity.SeriesEpisode;
import job.search.kg.exceptions.ResourceNotFoundException;
import job.search.kg.repo.SeriesEpisodeRepository;
import job.search.kg.repo.SeriesRepository;
import job.search.kg.service.MinioStorageService;
import job.search.kg.util.YouTube;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Раздел «Сериал»: первая серия открыта всем, остальные — при любом активном тарифе.
 * Видео лежат на YouTube («Доступ по ссылке»): ссылку и превью закрытой серии не отдаём вовсе.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BotSeriesService {

    private static final int VIDEO_URL_HOURS = 6;

    private final SeriesRepository seriesRepository;
    private final SeriesEpisodeRepository episodeRepository;
    private final BotSubscriptionService subscriptionService;
    private final MinioStorageService storage;

    @Transactional(readOnly = true)
    public List<SeriesResponse> list(Long telegramId) {
        boolean access = hasAccess(telegramId);
        return seriesRepository.findByIsActiveTrueOrderBySortOrderAscIdDesc().stream()
                .map(s -> base(s, access))
                .filter(r -> r.getEpisodesCount() > 0)
                .toList();
    }

    @Transactional(readOnly = true)
    public SeriesResponse get(Long id, Long telegramId) {
        Series s = seriesRepository.findById(id)
                .filter(x -> Boolean.TRUE.equals(x.getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Сериал не найден"));
        boolean access = hasAccess(telegramId);
        SeriesResponse r = base(s, access);
        r.setEpisodes(episodeRepository.findBySeriesIdAndIsPublishedTrueOrderByNumberAsc(id).stream()
                .map(e -> episode(e, access))
                .toList());
        return r;
    }

    /** Одна серия со ссылкой на видео; закрытая без тарифа — 403 */
    @Transactional(readOnly = true)
    public SeriesResponse.Episode episode(Long episodeId, Long telegramId) {
        SeriesEpisode e = episodeRepository.findByIdWithSeries(episodeId)
                .filter(x -> Boolean.TRUE.equals(x.getIsPublished()) && Boolean.TRUE.equals(x.getSeries().getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Серия не найдена"));
        boolean access = hasAccess(telegramId);
        if (!e.isOpenForAll() && !access) {
            throw new AccessDeniedException("Серия доступна с любым тарифом");
        }
        return episode(e, access);
    }

    private SeriesResponse base(Series s, boolean access) {
        SeriesResponse r = new SeriesResponse();
        r.setId(s.getId());
        r.setTitle(s.getTitle());
        r.setDescription(s.getDescription());
        String cover = safeUrl(s.getCoverUrl(), 1);
        if (cover == null) {
            // обложки нет — превью бесплатной 1-й серии с YouTube (её id и так открыт всем)
            cover = episodeRepository.findBySeriesIdAndIsPublishedTrueOrderByNumberAsc(s.getId()).stream()
                    .filter(SeriesEpisode::isOpenForAll).findFirst()
                    .map(e -> YouTube.thumbnail(YouTube.videoId(e.getVideoUrl()))).orElse(null);
        }
        r.setCoverUrl(cover);
        r.setEpisodesCount((int) episodeRepository.countBySeriesIdAndIsPublishedTrue(s.getId()));
        r.setHasAccess(access);
        return r;
    }

    private SeriesResponse.Episode episode(SeriesEpisode e, boolean access) {
        SeriesResponse.Episode r = new SeriesResponse.Episode();
        r.setId(e.getId());
        r.setNumber(e.getNumber());
        r.setTitle(e.getTitle());
        r.setDescription(e.getDescription());
        r.setDurationSec(e.getDurationSec());
        r.setFree(e.isOpenForAll());
        boolean locked = !e.isOpenForAll() && !access;
        r.setLocked(locked);
        String ytId = YouTube.videoId(e.getVideoUrl());
        String thumb = safeUrl(e.getThumbnailUrl(), 1);
        if (!locked) {
            r.setYoutubeId(ytId);
            r.setVideoUrl(ytId != null ? "https://youtu.be/" + ytId : safeUrl(e.getVideoUrl(), VIDEO_URL_HOURS));
            if (thumb == null) thumb = YouTube.thumbnail(ytId);
        }
        // у закрытой серии превью YouTube не отдаём: по нему видно id видео
        r.setThumbnailUrl(thumb);
        return r;
    }

    private boolean hasAccess(Long telegramId) {
        if (telegramId == null) return false;
        try {
            return subscriptionService.hasActiveSubscription(telegramId);
        } catch (Exception e) {
            return false; // ещё не зарегистрирован
        }
    }

    private String safeUrl(String value, int hours) {
        try {
            return storage.resolveUrl(value, hours);
        } catch (Exception ex) {
            log.warn("Series media URL failed for {}: {}", value, ex.getMessage());
            return null;
        }
    }
}
