package job.search.kg.service.user;

import job.search.kg.dto.response.DealResponse;
import job.search.kg.entity.*;
import job.search.kg.exceptions.ConflictException;
import job.search.kg.exceptions.ResourceNotFoundException;
import job.search.kg.repo.*;
import job.search.kg.service.MinioStorageService;
import job.search.kg.telegram.notification.DealNotificationService;
import job.search.kg.util.AfterCommit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/** Сделки (DESIGN_SPEC 7): подтверждение «работали вместе», баллы, отзывы, «Кого наняли?» */
@Slf4j
@Service
@RequiredArgsConstructor
public class BotDealService {

    public static final int POINTS = 30;
    public static final int BADGE_GOAL = 3;
    static final int ASK_DELAY_HOURS = 48;   // вопрос «Работали?» — через 2 дня после приглашения
    static final int AUTO_CONFIRM_HOURS = 48; // молчание второй стороны = согласие

    private final DealRepository deals;
    private final DealProofRepository proofs;
    private final UserRepository users;
    private final VacancyRepository vacancies;
    private final ApplicationQueries applications;
    private final CompanyRepository companies;
    private final CompanyReviewRepository companyReviews;
    private final BotPointsService points;
    private final MinioStorageService storage;
    private final DealNotificationService notifications;

    // ---------- Создание ----------

    /** Работодатель пригласил по отклику → через 2 дня спросим исполнителя «Работали?» */
    @Transactional
    public void onInvite(VacancyResponse application) {
        Vacancy v = application.getVacancy();
        User worker = application.getApplicant();
        if (deals.findByWorkerIdAndVacancyId(worker.getId(), v.getId()).isPresent()) return;
        Deal d = new Deal();
        d.setWorker(worker);
        d.setEmployer(v.getUser());
        d.setVacancy(v);
        d.setTitle(v.getTitle());
        d.setSource(Deal.Source.INVITE);
        d.setStatus(Deal.Status.ASK_WORKER);
        d.setAskAfter(LocalDateTime.now().plusHours(ASK_DELAY_HOURS));
        deals.save(d);
    }

    /**
     * «Закрыть набор» → «Кого наняли?». result: HIRED (workerIds обязательны), NONE, OUTSIDE.
     * Нанятым приходит «Подтвердите», на профиле компании растёт «Наняли через Work KG».
     */
    @Transactional
    public DealResponse.VacancyRow closeVacancy(Long telegramId, Long vacancyId, String result, List<Long> workerIds) {
        User employer = user(telegramId);
        Vacancy v = vacancies.findById(vacancyId).orElseThrow(() -> new ResourceNotFoundException("Вакансия не найдена"));
        if (!v.getUser().getId().equals(employer.getId())) throw new AccessDeniedException("Доступ запрещён");
        if (v.getCloseResult() != null) throw new ConflictException("Набор уже закрыт");
        Set<Long> wrote = applications.findByVacancy(vacancyId).stream()
                .map(a -> a.getApplicant().getId()).collect(Collectors.toSet());
        List<Long> hired = workerIds == null ? List.of() : workerIds.stream().distinct().filter(wrote::contains).toList();
        if ("HIRED".equals(result) && hired.isEmpty()) throw new IllegalArgumentException("Отметьте, кого наняли");
        if (!Set.of("HIRED", "NONE", "OUTSIDE").contains(result)) throw new IllegalArgumentException("Неизвестный итог");

        v.setIsActive(false);
        v.setCloseResult(hired.isEmpty() ? result : "HIRED");
        v.setClosedAt(LocalDateTime.now());
        vacancies.save(v);

        List<Long> toNotify = new ArrayList<>();
        for (Long wid : hired) {
            Deal d = deals.findByWorkerIdAndVacancyId(wid, vacancyId).orElseGet(() -> {
                Deal n = new Deal();
                n.setWorker(users.getReferenceById(wid));
                n.setEmployer(employer);
                n.setVacancy(v);
                n.setTitle(v.getTitle());
                n.setSource(Deal.Source.HIRE);
                return n;
            });
            if (d.getStatus() == null || d.getStatus() == Deal.Status.ASK_WORKER || d.getStatus() == Deal.Status.DECLINED) {
                d.setStatus(Deal.Status.AWAIT_WORKER);
                d.setDueAt(LocalDateTime.now().plusHours(AUTO_CONFIRM_HOURS));
                d = deals.save(d);
                toNotify.add(d.getId());
            }
        }
        if (!hired.isEmpty()) {
            companies.findByUser(employer).ifPresent(c -> {
                c.setHiredCount((c.getHiredCount() == null ? 0 : c.getHiredCount()) + hired.size());
                companies.save(c);
            });
        }
        AfterCommit.run(() -> toNotify.forEach(notifications::askWorkerToConfirmHire));
        return vacancyRow(v);
    }

    // ---------- Ответы ----------

    /** Исполнитель: «Вы работали с X?» — звезда = «да», null = «нет» */
    @Transactional
    public DealResponse workerAnswer(Long telegramId, Long dealId, Integer stars) {
        User me = user(telegramId);
        Deal d = mine(dealId, me, true);
        if (d.getStatus() != Deal.Status.ASK_WORKER) throw new ConflictException("Вы уже ответили");
        if (stars == null) {
            d.setStatus(Deal.Status.DECLINED);
            return toResponse(d, me);
        }
        d.setWorkerStars(stars(stars));
        d.setStatus(Deal.Status.AWAIT_EMPLOYER);
        d.setDueAt(LocalDateTime.now().plusHours(AUTO_CONFIRM_HOURS));
        award(d);
        syncCompanyReview(d);
        Long id = d.getId();
        AfterCommit.run(() -> notifications.askEmployerToConfirm(id));
        return toResponse(d, me);
    }

    /** Исполнитель: «{Компания} отметил, что вы у них работали» — Да / Не работал */
    @Transactional
    public DealResponse workerConfirm(Long telegramId, Long dealId, boolean yes) {
        User me = user(telegramId);
        Deal d = mine(dealId, me, true);
        if (d.getStatus() != Deal.Status.AWAIT_WORKER) throw new ConflictException("Вы уже ответили");
        if (yes) {
            confirm(d);
        } else {
            d.setStatus(Deal.Status.DECLINED);
        }
        return toResponse(d, me);
    }

    /** Работодатель: «Всё верно?» — звезда = подтверждение */
    @Transactional
    public DealResponse employerRate(Long telegramId, Long dealId, int stars) {
        User me = user(telegramId);
        Deal d = mine(dealId, me, false);
        if (d.getStatus() != Deal.Status.AWAIT_EMPLOYER) throw new ConflictException("Вы уже ответили");
        d.setEmployerStars(stars(stars));
        confirm(d);
        return toResponse(d, me);
    }

    /** Работодатель: «Он у нас не работал» — отзыв остаётся с пометкой «Компания оспорила» */
    @Transactional
    public DealResponse employerDispute(Long telegramId, Long dealId) {
        User me = user(telegramId);
        Deal d = mine(dealId, me, false);
        if (d.getStatus() != Deal.Status.AWAIT_EMPLOYER) throw new ConflictException("Вы уже ответили");
        d.setStatus(Deal.Status.DISPUTED);
        syncCompanyReview(d);
        return toResponse(d, me);
    }

    /** Исполнитель присылает доказательство по оспоренной сделке → модератор */
    @Transactional
    public DealResponse addProof(Long telegramId, Long dealId, String kind, MultipartFile file) throws Exception {
        User me = user(telegramId);
        Deal d = mine(dealId, me, true);
        if (d.getStatus() != Deal.Status.DISPUTED && d.getStatus() != Deal.Status.PROOF_REVIEW) {
            throw new ConflictException("Доказательство нужно только для оспоренной работы");
        }
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Выберите файл");
        if (file.getSize() > 10L * 1024 * 1024) throw new IllegalArgumentException("Файл больше 10 МБ");
        String type = file.getContentType() == null ? "" : file.getContentType();
        if (!type.startsWith("image/") && !type.equals("application/pdf")) {
            throw new IllegalArgumentException("Подходят фото, скриншот или PDF");
        }
        DealProof p = new DealProof();
        p.setDeal(d);
        p.setKind(DealProof.Kind.valueOf(kind));
        p.setFileUrl(storage.uploadProofFile(file, d.getId()));
        proofs.save(p);
        d.setStatus(Deal.Status.PROOF_REVIEW);
        return toResponse(d, me);
    }

    /** Отзыв (7.3): три критерия + текст. Исполнитель оценивает работодателя, работодатель — исполнителя. */
    @Transactional
    public DealResponse review(Long telegramId, Long dealId, int a, int b, int c, String text) {
        User me = user(telegramId);
        Deal d = deals.findFull(dealId).orElseThrow(() -> new ResourceNotFoundException("Сделка не найдена"));
        boolean worker = d.getWorker().getId().equals(me.getId());
        if (!worker && !d.getEmployer().getId().equals(me.getId())) throw new AccessDeniedException("Доступ запрещён");
        if (!reviewable(d, worker)) throw new ConflictException("Отзыв можно оставить после подтверждения работы");
        String t = text == null || text.isBlank() ? null : text.trim();
        if (t != null && t.length() > 1000) throw new IllegalArgumentException("Отзыв — до 1000 символов");
        if (worker) {
            d.setWorkerPaid(stars(a));
            d.setWorkerConditions(stars(b));
            d.setWorkerAttitude(stars(c));
            d.setWorkerText(t);
            if (d.getWorkerStars() == null) d.setWorkerStars((short) Math.round((a + b + c) / 3.0));
            syncCompanyReview(d);
        } else {
            d.setEmployerPunctual(stars(a));
            d.setEmployerQuality(stars(b));
            d.setEmployerCommunication(stars(c));
            d.setEmployerText(t);
            if (d.getEmployerStars() == null) d.setEmployerStars((short) Math.round((a + b + c) / 3.0));
        }
        return toResponse(d, me);
    }

    // ---------- Чтение ----------

    @Transactional(readOnly = true)
    public Map<String, Object> worker(Long telegramId) {
        User me = user(telegramId);
        LocalDateTime now = LocalDateTime.now();
        List<Deal> all = deals.findForWorker(me.getId());
        List<DealResponse> questions = all.stream()
                .filter(d -> d.getStatus() == Deal.Status.ASK_WORKER && d.getAskAfter() != null && !d.getAskAfter().isAfter(now))
                .map(d -> toResponse(d, me)).toList();
        List<DealResponse> requests = all.stream().filter(d -> d.getStatus() == Deal.Status.AWAIT_WORKER)
                .map(d -> toResponse(d, me)).toList();
        List<DealResponse> works = all.stream().filter(d -> Set.of(Deal.Status.AWAIT_EMPLOYER, Deal.Status.CONFIRMED,
                        Deal.Status.DISPUTED, Deal.Status.PROOF_REVIEW).contains(d.getStatus()))
                .map(d -> toResponse(d, me)).toList();
        return Map.of("summary", summary(me), "questions", questions, "requests", requests, "works", works);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> employer(Long telegramId) {
        User me = user(telegramId);
        List<Deal> all = deals.findForEmployer(me.getId());
        List<DealResponse> requests = all.stream().filter(d -> d.getStatus() == Deal.Status.AWAIT_EMPLOYER)
                .map(d -> toResponse(d, me)).toList();
        List<DealResponse> done = all.stream().filter(d -> Set.of(Deal.Status.CONFIRMED, Deal.Status.DISPUTED,
                        Deal.Status.PROOF_REVIEW, Deal.Status.AWAIT_WORKER).contains(d.getStatus()))
                .map(d -> toResponse(d, me)).toList();
        List<DealResponse.VacancyRow> rows = vacancies.findByUser(me).stream()
                .sorted(Comparator.comparing(Vacancy::getCreatedAt).reversed())
                .map(this::vacancyRow).toList();
        return Map.of("requests", requests, "vacancies", rows, "done", done);
    }

    @Transactional(readOnly = true)
    public DealResponse get(Long telegramId, Long dealId) {
        User me = user(telegramId);
        Deal d = deals.findFull(dealId).orElseThrow(() -> new ResourceNotFoundException("Сделка не найдена"));
        if (!d.getWorker().getId().equals(me.getId()) && !d.getEmployer().getId().equals(me.getId())) {
            throw new AccessDeniedException("Доступ запрещён");
        }
        return toResponse(d, me);
    }

    /** Для счётчика на табе «Сделки» */
    @Transactional(readOnly = true)
    public long pending(Long telegramId) {
        return summary(user(telegramId)).getPending();
    }

    /** Рейтинг исполнителей: userId → [avg, works] (для откликов и «Кого наняли?») */
    @Transactional(readOnly = true)
    public Map<Long, double[]> workerStats(Collection<Long> userIds) {
        if (userIds.isEmpty()) return Map.of();
        Map<Long, double[]> m = new HashMap<>();
        for (Object[] r : deals.workerStats(userIds)) {
            m.put((Long) r[0], new double[]{r[1] == null ? 0 : ((Number) r[1]).doubleValue(), ((Number) r[2]).doubleValue()});
        }
        return m;
    }

    /** «Подтверждает отметки сотрудников», % — null, пока нет данных */
    @Transactional(readOnly = true)
    public Integer confirmRatePct(Long employerUserId) {
        List<Object[]> rows = deals.confirmStats(employerUserId);
        if (rows.isEmpty() || rows.get(0)[0] == null) return null;
        long ok = ((Number) rows.get(0)[0]).longValue();
        long bad = rows.get(0)[1] == null ? 0 : ((Number) rows.get(0)[1]).longValue();
        return ok + bad == 0 ? null : (int) Math.round(ok * 100.0 / (ok + bad));
    }

    // ---------- Фоновые задачи (DealJobs) ----------

    /** Молчание второй стороны дольше срока = подтверждение */
    @Transactional
    public int autoConfirm() {
        int n = 0;
        for (Deal d : deals.findOverdue(LocalDateTime.now())) {
            confirm(d);
            n++;
        }
        return n;
    }

    /** Вопросы «Работали?», которым пора в бот */
    @Transactional
    public List<Long> dueQuestions() {
        List<Long> ids = new ArrayList<>();
        for (Deal d : deals.findQuestionsToSend(LocalDateTime.now())) {
            d.setNotifiedAt(LocalDateTime.now());
            ids.add(d.getId());
        }
        return ids;
    }

    // ---------- helpers ----------

    private void confirm(Deal d) {
        d.setStatus(Deal.Status.CONFIRMED);
        d.setConfirmedAt(LocalDateTime.now());
        award(d);
        syncCompanyReview(d);
    }

    /** +30 баллов исполнителю — один раз за сделку */
    private void award(Deal d) {
        if (Boolean.TRUE.equals(d.getPointsAwarded())) return;
        d.setPointsAwarded(true);
        try {
            points.addPoints(d.getWorker().getTelegramId(), POINTS, PointsTransaction.TransactionType.DEAL,
                    "Подтверждённая работа: " + d.getTitle());
        } catch (Exception e) {
            log.warn("Deal {} points failed: {}", d.getId(), e.getMessage());
        }
    }

    /** Оценка исполнителя → отзыв на профиле компании работодателя (с пометкой, если оспорено) */
    private void syncCompanyReview(Deal d) {
        if (d.getWorkerStars() == null) return;
        Company c = companies.findByUserId(d.getEmployer().getId()).orElse(null);
        if (c == null) return;
        CompanyReview r = companyReviews.findByDealId(d.getId()).orElseGet(CompanyReview::new);
        r.setDealId(d.getId());
        r.setCompany(c);
        r.setAuthor(d.getWorker());
        r.setAuthorRole(d.getTitle().length() > 80 ? d.getTitle().substring(0, 80) : d.getTitle());
        r.setTenureText("через Work KG");
        r.setStars(d.getWorkerStars());
        r.setPaidOnTime(d.getWorkerPaid());
        r.setConditions(d.getWorkerConditions());
        r.setAttitude(d.getWorkerAttitude());
        r.setText(d.getWorkerText());
        r.setDisputed(d.getStatus() == Deal.Status.DISPUTED);
        companyReviews.save(r);
    }

    /** Исполнитель пишет отзыв после своего «да» (даже если компания оспорила); работодатель — только по подтверждённой */
    private static boolean reviewable(Deal d, boolean worker) {
        if (!worker) return d.getStatus() == Deal.Status.CONFIRMED;
        return Set.of(Deal.Status.AWAIT_EMPLOYER, Deal.Status.CONFIRMED, Deal.Status.DISPUTED, Deal.Status.PROOF_REVIEW)
                .contains(d.getStatus());
    }

    private DealResponse.Summary summary(User me) {
        List<Deal> mineAsWorker = deals.findForWorker(me.getId());
        LocalDateTime now = LocalDateTime.now();
        long confirmed = mineAsWorker.stream().filter(d -> d.getStatus() == Deal.Status.CONFIRMED).count();
        long pending = mineAsWorker.stream().filter(d ->
                d.getStatus() == Deal.Status.AWAIT_WORKER
                        || d.getStatus() == Deal.Status.ASK_WORKER && d.getAskAfter() != null && !d.getAskAfter().isAfter(now)).count()
                + deals.findForEmployer(me.getId()).stream().filter(d -> d.getStatus() == Deal.Status.AWAIT_EMPLOYER).count();
        return DealResponse.Summary.builder().confirmedWorks(confirmed).badgeGoal(BADGE_GOAL)
                .verifiedWorker(confirmed >= BADGE_GOAL).pending(pending).build();
    }

    DealResponse.VacancyRow vacancyRow(Vacancy v) {
        List<VacancyResponse> apps = applications.findByVacancy(v.getId());
        Map<Long, double[]> stats = workerStats(apps.stream().map(a -> a.getApplicant().getId()).toList());
        List<DealResponse.Person> wrote = apps.stream().map(a -> {
            double[] s = stats.get(a.getApplicant().getId());
            return DealResponse.Person.builder()
                    .userId(a.getApplicant().getId()).applicationId(a.getId()).name(a.getResume().getName())
                    .rating(s == null || s[0] == 0 ? null : Math.round(s[0] * 10) / 10.0)
                    .works(s == null ? 0 : (long) s[1]).build();
        }).toList();
        List<String> hired = deals.findLiveByVacancy(v.getId()).stream()
                .filter(d -> d.getStatus() != Deal.Status.ASK_WORKER)
                .map(d -> ContactMasking.shortName(d.getWorker().getFirstName(), d.getWorker().getLastName()))
                .toList();
        boolean active = Boolean.TRUE.equals(v.getIsActive());
        String statusText = v.getCloseResult() != null || !active ? "Закрыта"
                : "Открыта " + daysText(v.getCreatedAt());
        return DealResponse.VacancyRow.builder().vacancyId(v.getId()).title(v.getTitle()).schedule(v.getSchedule())
                .active(active).closeResult(v.getCloseResult()).statusText(statusText).wrote(wrote).hired(hired).build();
    }

    private static String daysText(LocalDateTime created) {
        long d = created == null ? 0 : java.time.Duration.between(created, LocalDateTime.now()).toDays();
        return d == 0 ? "сегодня" : d + " дн.";
    }

    private DealResponse toResponse(Deal d, User me) {
        boolean worker = d.getWorker().getId().equals(me.getId());
        String otherName;
        Long otherCompanyId = null;
        boolean verified = false;
        if (worker) {
            Company c = companies.findByUserId(d.getEmployer().getId()).orElse(null);
            if (c != null) {
                otherName = c.getName();
                otherCompanyId = c.getId();
                verified = Boolean.TRUE.equals(c.getVerified());
            } else {
                otherName = ContactMasking.shortName(d.getEmployer().getFirstName(), d.getEmployer().getLastName());
            }
        } else {
            otherName = ContactMasking.shortName(d.getWorker().getFirstName(), d.getWorker().getLastName());
        }
        Short my = worker ? d.getWorkerStars() : d.getEmployerStars();
        Short other = worker ? d.getEmployerStars() : d.getWorkerStars();
        boolean reviewed = worker ? d.getWorkerPaid() != null : d.getEmployerPunctual() != null;
        return DealResponse.builder()
                .id(d.getId()).status(d.getStatus().name()).source(d.getSource().name())
                .side(worker ? "WORKER" : "EMPLOYER").title(d.getTitle()).whenText(whenText(d))
                .otherName(otherName).otherCompanyId(otherCompanyId).otherVerified(verified)
                .vacancyId(d.getVacancy() != null ? d.getVacancy().getId() : null)
                .gigId(d.getGig() != null ? d.getGig().getId() : null)
                .myStars(my == null ? null : (int) my)
                .otherStars(my != null && other != null ? (int) other : null)
                .dueAt(d.getDueAt()).canReview(reviewable(d, worker) && !reviewed).reviewed(reviewed)
                .createdAt(d.getCreatedAt())
                .build();
    }

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("ru"));

    private static String whenText(Deal d) {
        if (d.getGig() != null) return d.getGig().getWorkDate().format(DAY);
        LocalDateTime t = d.getConfirmedAt() != null ? d.getConfirmedAt() : d.getCreatedAt();
        return t == null ? "" : t.format(DAY);
    }

    private Deal mine(Long dealId, User me, boolean asWorker) {
        Deal d = deals.findFull(dealId).orElseThrow(() -> new ResourceNotFoundException("Сделка не найдена"));
        User side = asWorker ? d.getWorker() : d.getEmployer();
        if (!side.getId().equals(me.getId())) throw new AccessDeniedException("Доступ запрещён");
        return d;
    }

    private static Short stars(int v) {
        if (v < 1 || v > 5) throw new IllegalArgumentException("Оценка — от 1 до 5");
        return (short) v;
    }

    private User user(Long telegramId) {
        return users.findByTelegramId(telegramId).orElseThrow(() -> new ResourceNotFoundException("Пользователь не найден"));
    }
}
