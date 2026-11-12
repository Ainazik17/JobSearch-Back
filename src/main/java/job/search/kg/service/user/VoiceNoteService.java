package job.search.kg.service.user;

import job.search.kg.entity.User;
import job.search.kg.exceptions.ResourceNotFoundException;
import job.search.kg.repo.UserRepository;
import job.search.kg.service.MinioStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Map;

/**
 * Голосовое к объявлению (без распознавания). Сначала файл загружается сюда и возвращается ключ,
 * потом форма создаёт задание/вакансию с этим ключом — так рассылка сразу знает, что голосовое есть.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceNoteService {

    static final long MAX_BYTES = 3L * 1024 * 1024; // ~2 мин в opus/aac
    static final int MAX_SECONDS = 120;
    private static final Map<String, String> TYPES = Map.of(
            "audio/webm", ".webm",
            "audio/ogg", ".ogg",
            "audio/mp4", ".m4a",
            "audio/x-m4a", ".m4a",
            "audio/aac", ".aac",
            "audio/mpeg", ".mp3",
            "audio/wav", ".wav",
            "audio/x-wav", ".wav");

    private final UserRepository userRepository;
    private final MinioStorageService storage;

    public record Uploaded(String key, String url, int duration) {}

    public Uploaded upload(Long telegramId, MultipartFile file, Integer duration) throws Exception {
        User user = userRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Запишите голосовое");
        if (file.getSize() > MAX_BYTES) throw new IllegalArgumentException("Голосовое слишком длинное — до 2 минут");
        String type = file.getContentType() == null ? "" : file.getContentType().split(";")[0].trim().toLowerCase(Locale.ROOT);
        String ext = TYPES.get(type);
        if (ext == null) throw new IllegalArgumentException("Этот формат аудио не поддерживается");
        int sec = duration == null ? 0 : duration;
        if (sec < 1 || sec > MAX_SECONDS) throw new IllegalArgumentException("Голосовое — от 1 секунды до 2 минут");
        String key = storage.uploadVoiceFile(file, user.getId(), ext);
        return new Uploaded(key, storage.resolveUrl(key), sec);
    }

    /**
     * Проверить, что ключ загружен этим пользователем, и вернуть его.
     * null — не менять; "" — удалить (вернёт "").
     */
    public String claim(User owner, String key) {
        if (key == null) return null;
        if (key.isBlank()) return "";
        if (!key.contains("/voice/" + owner.getId() + "/") && !key.startsWith("voice/" + owner.getId() + "/")) {
            throw new IllegalArgumentException("Голосовое не найдено — запишите ещё раз");
        }
        return key;
    }

    public String resolve(String key) {
        if (key == null || key.isBlank()) return null;
        try {
            return storage.resolveUrl(key);
        } catch (Exception e) {
            return null;
        }
    }
}
