package job.search.kg.controller.user;

import job.search.kg.service.user.VoiceNoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Загрузка голосового до публикации объявления */
@RestController
@RequestMapping("/api/bot/voice")
@RequiredArgsConstructor
public class BotVoiceController {

    private final VoiceNoteService voiceNoteService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VoiceNoteService.Uploaded upload(@RequestParam Long telegramId,
                                            @RequestParam("file") MultipartFile file,
                                            @RequestParam Integer duration) throws Exception {
        return voiceNoteService.upload(telegramId, file, duration);
    }
}
