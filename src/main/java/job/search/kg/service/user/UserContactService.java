package job.search.kg.service.user;

import job.search.kg.entity.User;
import job.search.kg.repo.ResumeRepository;
import job.search.kg.repo.UserRepository;
import job.search.kg.repo.VacancyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Телефон для WhatsApp у подработки и «Моих услуг».
 * Mini App регистрирует пользователя без телефона, поэтому номер берём из формы;
 * если в форме пусто — из профиля, затем из последней вакансии/резюме этого пользователя.
 */
@Service
@RequiredArgsConstructor
public class UserContactService {

    private final UserRepository userRepository;
    private final VacancyRepository vacancyRepository;
    private final ResumeRepository resumeRepository;

    /** «0700 12-34-56», «+996 700 123 456» → «+996700123456»; null, если номер не похож на настоящий */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String d = raw.replaceAll("\\D", "");
        if (d.length() == 10 && d.startsWith("0")) d = "996" + d.substring(1);
        if (d.length() == 9) d = "996" + d;
        if (d.length() < 11 || d.length() > 15) return null;
        return "+" + d;
    }

    /**
     * Номер для объявления. Номер из формы запоминаем в профиле, чтобы в следующий раз подставить его сам.
     */
    public String phoneFor(User user, String requested) {
        String phone = normalize(requested);
        if (phone != null) {
            if (!phone.equals(user.getPhone())) {
                user.setPhone(phone);
                userRepository.save(user);
            }
            return phone;
        }
        return knownPhone(user);
    }

    /** Номер, который уже есть у пользователя (для подстановки в форму) */
    public String knownPhone(User user) {
        String p = normalize(user.getPhone());
        if (p != null) return p;
        p = vacancyRepository.findLatestPhone(user.getId()).stream().map(UserContactService::normalize)
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
        if (p != null) return p;
        return resumeRepository.findLatestPhone(user.getId()).stream().map(UserContactService::normalize)
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }
}
