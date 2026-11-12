package job.search.kg.service.user;

import job.search.kg.dto.request.user.WorkPlaceDto;
import job.search.kg.dto.response.ApplicationResponse;
import job.search.kg.entity.*;
import job.search.kg.exceptions.ConflictException;
import job.search.kg.exceptions.ResourceNotFoundException;
import job.search.kg.repo.*;
import job.search.kg.service.MinioStorageService;
import job.search.kg.telegram.notification.ApplicationNotificationService;
import job.search.kg.util.AfterCommit;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Отклик резюме на вакансию и экран кандидата для работодателя.
 * Контакты откликнувшегося работодатель видит без PRO: человек сам вышел на связь.
 */
@Service
@RequiredArgsConstructor
public class BotApplicationService {

    private final ApplicationQueries applications;
    private final VacancyRepository vacancyRepository;
    private final ResumeRepository resumeRepository;
    private final UserRepository userRepository;
    private final VacancyStatisticsRepository statisticsRepository;
    private final ResumeMediaRepository resumeMediaRepository;
    private final ResumeWorkPlaceRepository workPlaceRepository;
    private final CompanyRepository companyRepository;
    private final MinioStorageService storage;
    private final ApplicationNotificationService notifications;
    private final BotDealService dealService;

    @Transactional
    public ApplicationResponse apply(Long telegramId, Long vacancyId, Long resumeId, String message) {
        User applicant = user(telegramId);
        Vacancy vacancy = vacancyRepository.findByIdWithUser(vacancyId)
                .orElseThrow(() -> new ResourceNotFoundException("Вакансия не найдена"));
        if (!Boolean.TRUE.equals(vacancy.getIsActive())) {
            throw new ConflictException("Вакансия уже закрыта");
        }
        if (vacancy.getUser().getId().equals(applicant.getId())) {
            throw new ConflictException("Это ваша вакансия");
        }
        Resume resume = resumeId != null
                ? resumeRepository.findById(resumeId).orElseThrow(() -> new ResourceNotFoundException("Резюме не найдено"))
                : resumeRepository.findByUserAndIsActiveTrue(applicant).stream()
                    .max(Comparator.comparing(Resume::getCreatedAt))
                    .orElseThrow(() -> new ConflictException("Сначала создайте резюме"));
        if (!resume.getUser().getId().equals(applicant.getId())) {
            throw new AccessDeniedException("Можно откликаться только своим резюме");
        }
        if (!Boolean.TRUE.equals(resume.getIsActive())) {
            throw new ConflictException("Резюме скрыто — включите его в профиле, чтобы откликнуться");
        }
        if (applications.existsByVacancyIdAndApplicantId(vacancyId, applicant.getId())) {
            throw new ConflictException("Вы уже откликнулись на эту вакансию");
        }
        String mismatch = requirementsMismatch(vacancy, resume);
        if (mismatch != null) throw new ConflictException(mismatch);

        VacancyResponse r = VacancyResponse.builder()
                .vacancy(vacancy).resume(resume).applicant(applicant)
                .message(message == null || message.isBlank() ? null : message.trim())
                .status(VacancyResponse.ResponseStatus.PENDING)
                .build();
        try {
            r = applications.saveAndFlush(r);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // двойной тап: уникальный ключ (vacancy_id, resume_id)
            throw new ConflictException("Вы уже откликнулись на эту вакансию");
        }
        statisticsRepository.findByVacancyId(vacancyId).ifPresent(s -> {
            s.incrementResponses();
            statisticsRepository.save(s);
        });

        Long id = r.getId();
        AfterCommit.run(() -> notifications.notifyEmployer(id));
        return toResponse(r, false);
    }

    /** Отклики на вакансию — только владельцу */
    @Transactional(readOnly = true)
    public List<ApplicationResponse> forVacancy(Long telegramId, Long vacancyId) {
        User owner = user(telegramId);
        Vacancy v = vacancyRepository.findById(vacancyId)
                .orElseThrow(() -> new ResourceNotFoundException("Вакансия не найдена"));
        if (!v.getUser().getId().equals(owner.getId())) throw new AccessDeniedException("Доступ запрещён");
        return applications.findByVacancy(vacancyId).stream().map(r -> toResponse(r, true)).toList();
    }

    /** Мои отклики (соискатель) */
    @Transactional(readOnly = true)
    public List<ApplicationResponse> mine(Long telegramId) {
        User u = user(telegramId);
        return applications.findByApplicant(u.getId()).stream().map(r -> toResponse(r, false)).toList();
    }

    /** Счётчики по моим вакансиям: {vacancyId: {total, fresh}} */
    @Transactional(readOnly = true)
    public Map<Long, Map<String, Long>> counts(Long telegramId) {
        User u = user(telegramId);
        Map<Long, Map<String, Long>> m = new HashMap<>();
        for (Object[] row : applications.countsForOwner(u.getId())) {
            m.put((Long) row[0], Map.of("total", ((Number) row[1]).longValue(),
                    "fresh", row[2] == null ? 0L : ((Number) row[2]).longValue()));
        }
        return m;
    }

    /** Карточка отклика. Работодатель при открытии помечает её просмотренной. */
    @Transactional
    public ApplicationResponse get(Long telegramId, Long id) {
        User u = user(telegramId);
        VacancyResponse r = applications.findFull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Отклик не найден"));
        boolean employer = r.getVacancy().getUser().getId().equals(u.getId());
        boolean applicant = r.getApplicant().getId().equals(u.getId());
        if (!employer && !applicant) throw new AccessDeniedException("Доступ запрещён");
        if (employer && r.getStatus() == VacancyResponse.ResponseStatus.PENDING) {
            r.setStatus(VacancyResponse.ResponseStatus.VIEWED);
            r.setViewedAt(LocalDateTime.now());
        }
        return toResponse(r, employer);
    }

    /** INVITED или REJECTED — решает работодатель, кандидат получает сообщение в бот */
    @Transactional
    public ApplicationResponse decide(Long telegramId, Long id, String status) {
        User u = user(telegramId);
        VacancyResponse r = applications.findFull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Отклик не найден"));
        if (!r.getVacancy().getUser().getId().equals(u.getId())) throw new AccessDeniedException("Доступ запрещён");
        VacancyResponse.ResponseStatus next = switch (status) {
            case "INVITED" -> VacancyResponse.ResponseStatus.ACCEPTED;
            case "REJECTED" -> VacancyResponse.ResponseStatus.REJECTED;
            default -> throw new IllegalArgumentException("Статус: INVITED или REJECTED");
        };
        if (r.getStatus() != next) {
            r.setStatus(next);
            if (next == VacancyResponse.ResponseStatus.ACCEPTED) dealService.onInvite(r); // через 2 дня спросим «Работали?»
            if (r.getViewedAt() == null) r.setViewedAt(LocalDateTime.now());
            Long rid = r.getId();
            boolean invited = next == VacancyResponse.ResponseStatus.ACCEPTED;
            AfterCommit.run(() -> notifications.notifyCandidate(rid, invited));
        }
        return toResponse(r, true);
    }

    // ---------- helpers ----------

    static String apiStatus(VacancyResponse.ResponseStatus s) {
        return switch (s) {
            case PENDING -> "NEW";
            case VIEWED -> "VIEWED";
            case ACCEPTED -> "INVITED";
            case REJECTED -> "REJECTED";
        };
    }

    /** null — подходит, иначе причина по-русски */
    static String requirementsMismatch(Vacancy v, Resume r) {
        Integer age = r.getAge();
        if (age != null) {
            if (v.getMinAge() != null && v.getMinAge() > 0 && age < v.getMinAge()
                    || v.getMaxAge() != null && v.getMaxAge() > 0 && age > v.getMaxAge()) {
                return "Вакансия для возраста " + v.getMinAge() + "–" + v.getMaxAge() + " лет";
            }
        }
        if (v.getPreferredGender() == Vacancy.GenderPreference.MALE && r.getGender() == Resume.Gender.FEMALE) {
            return "Работодатель ищет мужчину";
        }
        if (v.getPreferredGender() == Vacancy.GenderPreference.FEMALE && r.getGender() == Resume.Gender.MALE) {
            return "Работодатель ищет женщину";
        }
        return null;
    }

    private ApplicationResponse toResponse(VacancyResponse r, boolean forEmployer) {
        Resume res = r.getResume();
        Vacancy v = r.getVacancy();
        boolean invited = r.getStatus() == VacancyResponse.ResponseStatus.ACCEPTED;
        String company = companyRepository.findByUserId(v.getUser().getId()).map(Company::getName)
                .orElse(v.getCompanyName());
        String photo = res.getProfilePhotoUrl();
        if (photo == null) {
            photo = resumeMediaRepository.findByResumeIdOrderByDisplayOrderAsc(res.getId()).stream()
                    .filter(m -> m.getMediaType() == BaseMedia.MediaType.PHOTO)
                    .map(ResumeMedia::getFileUrl).findFirst().orElse(null);
        }
        String resolved;
        try {
            resolved = storage.resolveUrl(photo);
        } catch (Exception e) {
            resolved = null;
        }
        List<WorkPlaceDto> places = workPlaceRepository.findByResumeIdOrderByDisplayOrderAscIdAsc(res.getId()).stream()
                .map(w -> new WorkPlaceDto(w.getPlace(), w.getRole(), w.getPeriodText())).toList();
        return ApplicationResponse.builder()
                .id(r.getId()).forEmployer(forEmployer).status(apiStatus(r.getStatus())).message(r.getMessage()).createdAt(r.getCreatedAt())
                .vacancyId(v.getId()).vacancyTitle(v.getTitle()).companyName(company).vacancySalary(v.getSalary())
                .employerPhone(!forEmployer && invited ? v.getPhone() : null)
                .employerUsername(!forEmployer && invited ? v.getUser().getUsername() : null)
                .resumeId(res.getId()).name(res.getName()).age(res.getAge())
                .gender(res.getGender() == null ? null : res.getGender().name())
                .cityName(res.getCity() != null ? res.getCity().getNameRu() : null)
                .categoryName(res.getCategory() != null ? res.getCategory().getNameRu() : null)
                .subcategoryName(res.getSubcategory() != null ? res.getSubcategory().getNameRu() : null)
                .experience(res.getExperience()).description(res.getDescription()).photoUrl(resolved)
                .phone(forEmployer ? (res.getPhone() != null ? res.getPhone() : res.getUser().getPhone()) : null)
                .telegramUsername(forEmployer ? res.getUser().getUsername() : null)
                .workPlaces(places)
                .workerRating(stats(r.getApplicant().getId())[0])
                .confirmedWorks((long) (double) stats(r.getApplicant().getId())[1])
                .build();
    }

    private Double[] stats(Long userId) {
        double[] s = dealService.workerStats(List.of(userId)).get(userId);
        return s == null ? new Double[]{null, 0.0} : new Double[]{s[0] == 0 ? null : Math.round(s[0] * 10) / 10.0, s[1]};
    }

    private User user(Long telegramId) {
        return userRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));
    }
}
