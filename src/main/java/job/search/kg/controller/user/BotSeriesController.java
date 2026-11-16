package job.search.kg.controller.user;

import job.search.kg.dto.response.SeriesResponse;
import job.search.kg.service.user.BotSeriesService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Раздел «Сериал» в Mini App */
@RestController
@RequestMapping("/api/bot/series")
@RequiredArgsConstructor
public class BotSeriesController {

    private final BotSeriesService seriesService;

    @GetMapping
    public List<SeriesResponse> list(@RequestParam(required = false) Long telegramId) {
        return seriesService.list(telegramId);
    }

    /** Сериал со списком серий; у закрытых locked = true и без videoUrl */
    @GetMapping("/{id}")
    public SeriesResponse get(@PathVariable Long id, @RequestParam(required = false) Long telegramId) {
        return seriesService.get(id, telegramId);
    }

    /** Свежая ссылка на видео серии (403, если нужен тариф) */
    @GetMapping("/episodes/{episodeId}")
    public SeriesResponse.Episode episode(@PathVariable Long episodeId, @RequestParam Long telegramId) {
        return seriesService.episode(episodeId, telegramId);
    }
}
