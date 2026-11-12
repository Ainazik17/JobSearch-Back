package job.search.kg.service.user;

import job.search.kg.dto.response.VacancyResponse;
import job.search.kg.entity.SavedVacancy;
import job.search.kg.entity.User;
import job.search.kg.entity.Vacancy;
import job.search.kg.exceptions.ResourceNotFoundException;
import job.search.kg.repo.SavedVacancyRepository;
import job.search.kg.repo.UserRepository;
import job.search.kg.repo.VacancyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** «Сохранённые» вакансии и настройки уведомлений пользователя */
@Service
@RequiredArgsConstructor
public class BotSavedService {

    private final SavedVacancyRepository savedRepository;
    private final VacancyRepository vacancyRepository;
    private final UserRepository userRepository;
    private final BotSearchService searchService;
    private final ContactMasking contactMasking;

    @Transactional
    public void save(Long telegramId, Long vacancyId, boolean on) {
        User u = user(telegramId);
        if (!on) {
            savedRepository.deleteByUserAndVacancy(u.getId(), vacancyId);
            return;
        }
        if (savedRepository.existsByUserIdAndVacancyId(u.getId(), vacancyId)) return;
        Vacancy v = vacancyRepository.findById(vacancyId)
                .orElseThrow(() -> new ResourceNotFoundException("Вакансия не найдена"));
        SavedVacancy s = new SavedVacancy();
        s.setUser(u);
        s.setVacancy(v);
        try {
            savedRepository.saveAndFlush(s);
        } catch (DataIntegrityViolationException ignored) {
            // двойной тап — уже сохранено
        }
    }

    @Transactional(readOnly = true)
    public List<Long> ids(Long telegramId) {
        return savedRepository.findVacancyIds(user(telegramId).getId());
    }

    /** Сохранённые: активные сверху; контакты — по тем же правилам, что и в поиске */
    @Transactional(readOnly = true)
    public List<VacancyResponse> list(Long telegramId) {
        User u = user(telegramId);
        return savedRepository.findWithVacancy(u.getId()).stream()
                .map(SavedVacancy::getVacancy)
                .sorted((a, b) -> Boolean.compare(Boolean.TRUE.equals(b.getIsActive()), Boolean.TRUE.equals(a.getIsActive())))
                .map(v -> {
                    boolean open = contactMasking.canSeeContacts(telegramId, v.getUser().getTelegramId());
                    VacancyResponse r = open ? searchService.mapVacancyToResponse(v) : searchService.mapVacancyToResponseWithoutSubs(v);
                    r.setIsActive(v.getIsActive());
                    return r;
                })
                .toList();
    }

    // ---------- Уведомления ----------

    @Transactional(readOnly = true)
    public Map<String, Boolean> notifySettings(Long telegramId) {
        User u = user(telegramId);
        return Map.of(
                "vacancies", !Boolean.FALSE.equals(u.getNotifyVacancies()),
                "resumes", !Boolean.FALSE.equals(u.getNotifyResumes()),
                "gigs", !Boolean.FALSE.equals(u.getNotifyGigs()),
                "companies", !Boolean.FALSE.equals(u.getNotifyCompanies()));
    }

    @Transactional
    public Map<String, Boolean> updateNotify(Long telegramId, Map<String, Boolean> patch) {
        User u = user(telegramId);
        if (patch.containsKey("vacancies")) u.setNotifyVacancies(Boolean.TRUE.equals(patch.get("vacancies")));
        if (patch.containsKey("resumes")) u.setNotifyResumes(Boolean.TRUE.equals(patch.get("resumes")));
        if (patch.containsKey("gigs")) u.setNotifyGigs(Boolean.TRUE.equals(patch.get("gigs")));
        if (patch.containsKey("companies")) u.setNotifyCompanies(Boolean.TRUE.equals(patch.get("companies")));
        userRepository.save(u);
        return notifySettings(telegramId);
    }

    private User user(Long telegramId) {
        return userRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));
    }
}
