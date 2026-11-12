package job.search.kg.service.user;

import job.search.kg.dto.request.user.CreateGigRequest;
import job.search.kg.dto.response.GigResponse;
import job.search.kg.entity.City;
import job.search.kg.entity.Gig;
import job.search.kg.entity.User;
import job.search.kg.exceptions.ResourceNotFoundException;
import job.search.kg.repo.CityRepository;
import job.search.kg.repo.GigRepository;
import job.search.kg.repo.UserRepository;
import job.search.kg.service.LocationService;
import job.search.kg.telegram.notification.GigNotificationService;
import job.search.kg.util.AfterCommit;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BotGigService {

    private static final ZoneId BISHKEK = ZoneId.of("Asia/Bishkek");

    private final GigRepository gigRepository;
    private final UserRepository userRepository;
    private final CityRepository cityRepository;
    private final LocationService locationService;
    private final ContactMasking contactMasking;
    private final GigNotificationService gigNotificationService;
    private final VoiceNoteService voiceNoteService;
    private final job.search.kg.repo.CompanyRepository companyRepository;
    private final job.search.kg.service.MinioStorageService storage;

    @Transactional
    public GigResponse create(Long telegramId, CreateGigRequest req) {
        User user = userRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));
        City city = cityRepository.findById(req.getCityId() != null ? req.getCityId() : 1)
                .orElseThrow(() -> new ResourceNotFoundException("Город не найден"));
        String voice = voiceNoteService.claim(user, req.getVoiceKey());
        boolean hasVoice = voice != null && !voice.isEmpty();
        if (!hasVoice && req.getTitle().trim().length() < 10) {
            throw new IllegalArgumentException("Опишите задание — минимум 10 символов");
        }
        if (req.getWorkDate().isBefore(LocalDate.now(BISHKEK))) {
            throw new IllegalArgumentException("Дата уже прошла");
        }
        if (req.getWorkDate().isAfter(LocalDate.now(BISHKEK).plusDays(60))) {
            throw new IllegalArgumentException("Разовое задание — не дальше чем на 2 месяца вперёд");
        }

        Gig gig = new Gig();
        gig.setUser(user);
        gig.setCity(city);
        gig.setTitle(req.getTitle().trim());
        gig.setWorkDate(req.getWorkDate());
        gig.setTimeText(req.getTimeText());
        gig.setPay(req.getPay());
        gig.setPayUnit(req.getPayUnit() != null ? req.getPayUnit() : Gig.PayUnit.TOTAL);
        gig.setPeopleCount(req.getPeopleCount() != null ? req.getPeopleCount() : "1");
        gig.setAddress(req.getAddress().trim());
        gig.setLatitude(req.getLatitude());
        gig.setLongitude(req.getLongitude());
        gig.setUrgent(Boolean.TRUE.equals(req.getUrgent()));
        gig.setPhone(user.getPhone());
        gig.setIsActive(true);
        if (hasVoice) {
            gig.setVoiceUrl(voice);
            gig.setVoiceDuration(req.getVoiceDuration());
        }
        gig = gigRepository.save(gig);

        Long id = gig.getId();
        AfterCommit.run(() -> gigNotificationService.notifyWorkersAboutNewGig(id));
        return toResponse(gig, telegramId, null, null);
    }

    /** Лента «Сегодня и завтра» */
    @Transactional(readOnly = true)
    public List<GigResponse> feed(Long viewerTelegramId, Integer cityId, Double lat, Double lon) {
        LocalDate today = LocalDate.now(BISHKEK);
        return gigRepository.findFeed(today, today.plusDays(1), cityId).stream()
                .map(g -> toResponse(g, viewerTelegramId, lat, lon))
                .toList();
    }

    /** «Надёжные заказчики»: кто чаще всех даёт подработку за 90 дней; открытые задания — сверху */
    @Transactional(readOnly = true)
    public List<job.search.kg.dto.response.GigCustomerResponse> topCustomers(Long viewerTelegramId, int limit) {
        LocalDate today = LocalDate.now(BISHKEK);
        java.util.Map<Long, List<Gig>> byUser = gigRepository.findSince(today.minusDays(90).atStartOfDay()).stream()
                .collect(java.util.stream.Collectors.groupingBy(g -> g.getUser().getId()));
        return byUser.values().stream()
                .map(gigs -> {
                    User u = gigs.get(0).getUser();
                    var company = companyRepository.findByUserId(u.getId()).orElse(null);
                    List<GigResponse> open = gigs.stream()
                            .filter(g -> Boolean.TRUE.equals(g.getIsActive()) && !g.getWorkDate().isBefore(today))
                            .sorted(java.util.Comparator.comparing(Gig::getWorkDate))
                            .map(g -> toResponse(g, viewerTelegramId, null, null))
                            .toList();
                    String cover = null;
                    if (company != null && company.getCoverUrl() != null) {
                        try { cover = storage.resolveUrl(company.getCoverUrl()); } catch (Exception ignored) { }
                    }
                    return job.search.kg.dto.response.GigCustomerResponse.builder()
                            .userId(u.getId())
                            .name(company != null ? company.getName() : ContactMasking.shortName(u.getFirstName(), u.getLastName()))
                            .companyId(company != null ? company.getId() : null)
                            .verified(company != null && Boolean.TRUE.equals(company.getVerified()))
                            .coverUrl(cover)
                            .type(company != null && company.getType() != null ? company.getType() : "Частное лицо")
                            .gigsCount(gigs.size())
                            .openGigs(open)
                            .build();
                })
                .sorted(java.util.Comparator
                        .comparing((job.search.kg.dto.response.GigCustomerResponse c) -> !c.getOpenGigs().isEmpty()).reversed()
                        .thenComparing(job.search.kg.dto.response.GigCustomerResponse::isVerified, java.util.Comparator.reverseOrder())
                        .thenComparing(job.search.kg.dto.response.GigCustomerResponse::getGigsCount, java.util.Comparator.reverseOrder()))
                .limit(limit)
                .toList();
    }

    @Transactional(readOnly = true)
    public GigResponse get(Long id, Long viewerTelegramId) {
        Gig gig = gigRepository.findByIdWithUser(id)
                .orElseThrow(() -> new ResourceNotFoundException("Задание не найдено"));
        return toResponse(gig, viewerTelegramId, null, null);
    }

    @Transactional(readOnly = true)
    public List<GigResponse> mine(Long telegramId) {
        User user = userRepository.findByTelegramId(telegramId)
                .orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));
        return gigRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(g -> toResponse(g, telegramId, null, null))
                .toList();
    }

    @Transactional
    public GigResponse setActive(Long id, Long telegramId, boolean active) {
        Gig gig = owned(id, telegramId);
        gig.setIsActive(active);
        return toResponse(gigRepository.save(gig), telegramId, null, null);
    }

    @Transactional
    public void delete(Long id, Long telegramId) {
        gigRepository.delete(owned(id, telegramId));
    }

    private Gig owned(Long id, Long telegramId) {
        Gig gig = gigRepository.findByIdWithUser(id)
                .orElseThrow(() -> new ResourceNotFoundException("Задание не найдено"));
        if (!gig.getUser().getTelegramId().equals(telegramId)) {
            throw new AccessDeniedException("Это не ваше задание");
        }
        return gig;
    }

    private GigResponse toResponse(Gig g, Long viewerTelegramId, Double lat, Double lon) {
        GigResponse r = new GigResponse();
        r.setId(g.getId());
        r.setTitle(g.getTitle());
        r.setWorkDate(g.getWorkDate());
        r.setTimeText(g.getTimeText());
        r.setPay(g.getPay());
        r.setPayUnit(g.getPayUnit());
        r.setPeopleCount(g.getPeopleCount());
        r.setAddress(g.getAddress());
        r.setLatitude(g.getLatitude());
        r.setLongitude(g.getLongitude());
        if (lat != null && lon != null && g.getLatitude() != null && g.getLongitude() != null) {
            r.setDistanceKm(locationService.calculateDistance(lat, lon, g.getLatitude(), g.getLongitude()));
        }
        r.setUrgent(g.getUrgent());
        r.setCityId(g.getCity().getId());
        r.setCityName(g.getCity().getNameRu());
        User author = g.getUser();
        r.setAuthorName(ContactMasking.shortName(author.getFirstName(), author.getLastName()));
        boolean open = contactMasking.canSeeContacts(viewerTelegramId, author.getTelegramId());
        r.setPhone(open ? g.getPhone() : ContactMasking.mask(g.getPhone()));
        r.setTelegramUsername(open ? author.getUsername() : null);
        r.setIsActive(g.getIsActive());
        r.setMine(author.getTelegramId().equals(viewerTelegramId));
        r.setCreatedAt(g.getCreatedAt());
        r.setVoiceUrl(voiceNoteService.resolve(g.getVoiceUrl()));
        r.setVoiceDuration(g.getVoiceUrl() == null ? null : g.getVoiceDuration());
        return r;
    }
}
