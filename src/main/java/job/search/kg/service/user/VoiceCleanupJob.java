package job.search.kg.service.user;

import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Голосовые, которые записали, но так и не опубликовали: раз в сутки удаляем файлы из voice/,
 * на которые не ссылается ни одно задание или вакансия и которые старше суток.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VoiceCleanupJob {

    private final MinioClient minio;

    @PersistenceContext
    private EntityManager em;

    @Value("${minio.bucket}")
    private String bucket;

    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Bishkek")
    @Transactional(readOnly = true)
    public void cleanup() {
        try {
            @SuppressWarnings("unchecked")
            List<String> urls = em.createNativeQuery("""
                    SELECT voice_url FROM gigs WHERE voice_url IS NOT NULL
                    UNION SELECT voice_url FROM vacancies WHERE voice_url IS NOT NULL
                    """).getResultList();
            Set<String> used = new HashSet<>();
            for (String u : urls) {
                int i = u.indexOf("voice/");
                if (i >= 0) used.add(u.substring(i).split("\\?")[0]);
            }
            ZonedDateTime cutoff = ZonedDateTime.now().minusDays(1);
            int removed = 0;
            for (Result<Item> r : minio.listObjects(ListObjectsArgs.builder().bucket(bucket).prefix("voice/").recursive(true).build())) {
                Item it = r.get();
                if (used.contains(it.objectName()) || it.lastModified() == null || it.lastModified().isAfter(cutoff)) continue;
                minio.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(it.objectName()).build());
                removed++;
            }
            if (removed > 0) log.info("Voice cleanup: removed {} unused files", removed);
        } catch (Exception e) {
            log.error("Voice cleanup failed: {}", e.getMessage(), e);
        }
    }
}
