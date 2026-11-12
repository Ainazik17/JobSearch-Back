package job.search.kg.service.user;

import job.search.kg.dto.request.user.UpsertCompanyRequest;
import job.search.kg.dto.response.CompanyResponse;
import job.search.kg.dto.response.CompanyReviewResponse;
import job.search.kg.dto.response.VacancyResponse;
import job.search.kg.entity.*;
import job.search.kg.exceptions.ResourceNotFoundException;
import job.search.kg.repo.*;
import job.search.kg.service.MinioStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BotCompanyService {

    static final int MAX_PHOTOS = 12;
    static final long MAX_PHOTO_BYTES = 8L * 1024 * 1024;
    private static final Set<String> PHOTO_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final CompanyRepository companyRepository;
    private final CompanyPhotoRepository photoRepository;
    private final CompanyFollowerRepository followerRepository;
    private final CompanyReviewRepository reviewRepository;
    private final VacancyRepository vacancyRepository;
    private final UserRepository userRepository;
    private final CityRepository cityRepository;
    private final MinioStorageService storage;
    private final BotSearchService searchService;
    private final ContactMasking contactMasking;
    private final BotDealService dealService;

    /** Агрегаты отзывов одной компании */
    record Stats(long count, Double rating, Integer paidPct, Double paid, Double conditions, Double attitude) {
        static final Stats EMPTY = new Stats(0, null, null, null, null, null);
    }

    // ---------- Чтение ----------

    /** «Компании»: поиск по названию и сфере; сортировка — проверенные, рейтинг, число вакансий */
    @Transactional(readOnly = true)
    public List<CompanyResponse> list(Long telegramId, String q, String sphere) {
        User viewer = telegramId == null ? null : userRepository.findByTelegramId(telegramId).orElse(null);
        Set<Long> followed = viewer == null ? Set.of() : new HashSet<>(followerRepository.findCompanyIdsByUserId(viewer.getId()));
        Map<Long, Stats> stats = allStats();
        Map<Long, Long> vacancies = activeVacanciesByUser();
        String needle = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);

        return companyRepository.findAllVisible().stream()
                .filter(c -> needle.isEmpty() || c.getName().toLowerCase(Locale.ROOT).contains(needle)
                        || (c.getType() != null && c.getType().toLowerCase(Locale.ROOT).contains(needle)))
                .filter(c -> sphere == null || sphere.isBlank() || sphere.equals(c.getSphere()))
                .map(c -> card(c, stats.getOrDefault(c.getId(), Stats.EMPTY),
                        vacancies.getOrDefault(c.getUser().getId(), 0L),
                        followed.contains(c.getId()), viewer != null && viewer.getId().equals(c.getUser().getId())))
                .sorted(Comparator.comparing(CompanyResponse::isVerified).reversed()
                        .thenComparing(r -> r.getRating() == null ? 0 : r.getRating(), Comparator.reverseOrder())
                        .thenComparing(CompanyResponse::getVacanciesCount, Comparator.reverseOrder())
                        .thenComparing(CompanyResponse::getId, Comparator.reverseOrder()))
                .toList();
    }

    @Transactional(readOnly = true)
    public CompanyResponse get(Long id, Long telegramId) {
        Company c = companyRepository.findByIdWithUser(id)
                .orElseThrow(() -> new ResourceNotFoundException("Компания не найдена"));
        User viewer = telegramId == null ? null : userRepository.findByTelegramId(telegramId).orElse(null);
        return detail(c, viewer);
    }

    /** Своя компания или null */
    @Transactional(readOnly = true)
    public CompanyResponse mine(Long telegramId) {
        User user = user(telegramId);
        return companyRepository.findByUser(user).map(c -> detail(c, user)).orElse(null);
    }

    /** Компания автора вакансии — для ссылки «{Компания} ✓ ›» на карточке вакансии */
    @Transactional(readOnly = true)
    public CompanyResponse byVacancy(Long vacancyId) {
        Vacancy v = vacancyRepository.findById(vacancyId).orElse(null);
        if (v == null) return null;
        return companyRepository.findByUserId(v.getUser().getId())
                .map(c -> CompanyResponse.builder().id(c.getId()).name(c.getName())
                        .verified(Boolean.TRUE.equals(c.getVerified())).coverUrl(resolve(c.getCoverUrl())).build())
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<VacancyResponse> vacancies(Long companyId, Long telegramId) {
        Company c = companyRepository.findByIdWithUser(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Компания не найдена"));
        boolean open = contactMasking.canSeeContacts(telegramId, c.getUser().getTelegramId());
        return vacancyRepository.findByUser(c.getUser()).stream()
                .filter(v -> Boolean.TRUE.equals(v.getIsActive()))
                .sorted(Comparator.comparing(Vacancy::getCreatedAt).reversed())
                .map(v -> {
                    VacancyResponse r = open ? searchService.mapVacancyToResponse(v)
                            : searchService.mapVacancyToResponseWithoutSubs(v);
                    if (!open) r.setTelegramUsername(null);
                    if (r.getCompanyName() == null || r.getCompanyName().isBlank()) r.setCompanyName(c.getName());
                    return r;
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CompanyReviewResponse> reviews(Long companyId) {
        return reviewRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .map(r -> CompanyReviewResponse.builder()
                        .id(r.getId()).authorRole(r.getAuthorRole()).tenure(r.getTenureText())
                        .stars(r.getStars()).text(r.getText()).disputed(Boolean.TRUE.equals(r.getDisputed()))
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CompanyResponse> following(Long telegramId) {
        User user = user(telegramId);
        Set<Long> ids = new HashSet<>(followerRepository.findCompanyIdsByUserId(user.getId()));
        if (ids.isEmpty()) return List.of();
        Map<Long, Stats> stats = allStats();
        Map<Long, Long> vacancies = activeVacanciesByUser();
        return companyRepository.findAllById(ids).stream()
                .map(c -> card(c, stats.getOrDefault(c.getId(), Stats.EMPTY),
                        vacancies.getOrDefault(c.getUser().getId(), 0L), true, false))
                .toList();
    }

    // ---------- Изменение ----------

    @Transactional
    public CompanyResponse upsert(Long telegramId, UpsertCompanyRequest req) {
        User user = user(telegramId);
        Company c = companyRepository.findByUser(user).orElseGet(() -> {
            Company n = new Company();
            n.setUser(user);
            return n;
        });
        c.setName(req.getName().trim());
        c.setType(blankToNull(req.getType()));
        c.setSphere(blankToNull(req.getSphere()));
        c.setTagline(blankToNull(req.getTagline()));
        c.setAbout(blankToNull(req.getAbout()));
        c.setStaff(blankToNull(req.getStaff()));
        List<String> perks = req.getPerks() == null ? List.of() : req.getPerks().stream()
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(s -> s.replace(Company.PERK_SEPARATOR, "/")).distinct().toList();
        c.setPerks(perks.isEmpty() ? null : String.join(Company.PERK_SEPARATOR, perks));
        if (req.getCityId() != null) {
            cityRepository.findById(req.getCityId()).ifPresent(c::setCity);
        } else if (c.getCity() == null) {
            cityRepository.findById(1).ifPresent(c::setCity);
        }
        c = companyRepository.save(c);
        return detail(c, user);
    }

    @Transactional
    public void follow(Long companyId, Long telegramId, boolean on) {
        User user = user(telegramId);
        Company c = companyRepository.findByIdWithUser(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Компания не найдена"));
        if (c.getUser().getId().equals(user.getId())) return; // на свою компанию не подписываемся
        Optional<CompanyFollower> existing = followerRepository.findByCompanyIdAndUserId(companyId, user.getId());
        if (on && existing.isEmpty()) {
            CompanyFollower f = new CompanyFollower();
            f.setCompany(c);
            f.setUser(user);
            followerRepository.save(f);
        } else if (!on) {
            existing.ifPresent(followerRepository::delete);
        }
    }

    /** Фото в ленту или обложка (cover=true) */
    @Transactional
    public CompanyResponse addPhoto(Long telegramId, MultipartFile file, String caption, boolean cover) throws Exception {
        User user = user(telegramId);
        Company c = companyRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Сначала создайте профиль компании"));
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Выберите фото");
        if (file.getSize() > MAX_PHOTO_BYTES) throw new IllegalArgumentException("Фото больше 8 МБ — выберите поменьше");
        if (file.getContentType() == null || !PHOTO_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("Подходят только JPG, PNG или WEBP");
        }
        String url = storage.uploadCompanyFile(file, c.getId());
        if (cover) {
            String old = c.getCoverUrl();
            c.setCoverUrl(url);
            companyRepository.save(c);
            deleteQuietly(old);
        } else {
            if (photoRepository.countByCompanyId(c.getId()) >= MAX_PHOTOS) {
                deleteQuietly(url);
                throw new IllegalArgumentException("Можно добавить до " + MAX_PHOTOS + " фото");
            }
            CompanyPhoto p = new CompanyPhoto();
            p.setCompany(c);
            p.setFileUrl(url);
            p.setCaption(caption == null || caption.isBlank() ? null : caption.trim().substring(0, Math.min(60, caption.trim().length())));
            p.setDisplayOrder((int) photoRepository.countByCompanyId(c.getId()));
            photoRepository.save(p);
        }
        return detail(c, user);
    }

    @Transactional
    public void deletePhoto(Long telegramId, Long photoId) {
        User user = user(telegramId);
        CompanyPhoto p = photoRepository.findById(photoId)
                .orElseThrow(() -> new ResourceNotFoundException("Фото не найдено"));
        if (!p.getCompany().getUser().getId().equals(user.getId())) throw new AccessDeniedException("Доступ запрещён");
        photoRepository.delete(p);
        deleteQuietly(p.getFileUrl());
    }

    // ---------- Маппинг ----------

    private CompanyResponse card(Company c, Stats s, long vacanciesCount, boolean following, boolean mine) {
        return CompanyResponse.builder()
                .id(c.getId()).name(c.getName()).type(c.getType()).sphere(c.getSphere())
                .cityName(c.getCity() != null ? c.getCity().getNameRu() : null)
                .tagline(c.getTagline()).coverUrl(resolve(c.getCoverUrl()))
                .verified(Boolean.TRUE.equals(c.getVerified()))
                .rating(s.rating()).reviewsCount(s.count()).paidOnTimePct(s.paidPct())
                .vacanciesCount(vacanciesCount).following(following).mine(mine)
                .build();
    }

    private CompanyResponse detail(Company c, User viewer) {
        Stats s = stats(c.getId());
        long vac = vacancyRepository.findByUser(c.getUser()).stream().filter(v -> Boolean.TRUE.equals(v.getIsActive())).count();
        boolean following = viewer != null && followerRepository.findByCompanyIdAndUserId(c.getId(), viewer.getId()).isPresent();
        boolean mine = viewer != null && viewer.getId().equals(c.getUser().getId());
        CompanyResponse r = card(c, s, vac, following, mine);
        r.setAbout(c.getAbout());
        r.setPerks(c.getPerks() == null ? List.of() : List.of(c.getPerks().split("\\" + Company.PERK_SEPARATOR)));
        r.setStaff(c.getStaff());
        r.setSince(c.getCreatedAt() != null ? c.getCreatedAt().getYear() : null);
        r.setHiredCount(c.getHiredCount() == null ? 0 : c.getHiredCount());
        r.setConfirmRatePct(dealService.confirmRatePct(c.getUser().getId()));
        r.setFollowersCount(followerRepository.findByCompanyIdWithUser(c.getId()).size());
        r.setPhotos(photoRepository.findByCompanyIdOrderByDisplayOrderAscIdAsc(c.getId()).stream()
                .map(p -> CompanyResponse.Photo.builder().id(p.getId()).url(resolve(p.getFileUrl())).caption(p.getCaption()).build())
                .toList());
        r.setBars(CompanyResponse.Bars.builder()
                .paidOnTime(s.paid()).conditions(s.conditions()).attitude(s.attitude()).build());
        return r;
    }

    private Map<Long, Stats> allStats() {
        Map<Long, Stats> m = new HashMap<>();
        for (Object[] row : reviewRepository.aggregateAll()) {
            m.put((Long) row[0], toStats(row));
        }
        return m;
    }

    private Stats stats(Long companyId) {
        return allStats().getOrDefault(companyId, Stats.EMPTY);
    }

    static Stats toStats(Object[] row) {
        long count = ((Number) row[1]).longValue();
        if (count == 0) return Stats.EMPTY;
        Double paidShare = row[3] == null ? null : ((Number) row[3]).doubleValue();
        return new Stats(count, round1(row[2]),
                paidShare == null ? null : (int) Math.round(paidShare * 100),
                round1(row[4]), round1(row[5]), round1(row[6]));
    }

    private static Double round1(Object v) {
        return v == null ? null : Math.round(((Number) v).doubleValue() * 10) / 10.0;
    }

    private Map<Long, Long> activeVacanciesByUser() {
        return companyRepository.countActiveVacanciesByUser().stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> ((Number) r[1]).longValue()));
    }

    private String resolve(String url) {
        try {
            return storage.resolveUrl(url);
        } catch (Exception e) {
            return null;
        }
    }

    private void deleteQuietly(String url) {
        if (url == null) return;
        try {
            String key = url.startsWith("http") ? storage.extractObjectNameFromUrl(url) : url;
            storage.deleteFile(storage.extractBucketFromUrl(url), key);
        } catch (Exception e) {
            log.warn("Could not delete company file {}: {}", url, e.getMessage());
        }
    }

    private User user(Long telegramId) {
        return userRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
