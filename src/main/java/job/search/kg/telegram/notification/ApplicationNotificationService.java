package job.search.kg.telegram.notification;

import job.search.kg.entity.*;
import job.search.kg.repo.ApplicationQueries;
import job.search.kg.repo.CompanyRepository;
import job.search.kg.repo.ResumeWorkPlaceRepository;
import job.search.kg.telegram.TelegramService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static job.search.kg.telegram.TelegramService.urlButton;
import static job.search.kg.telegram.TelegramService.webAppButton;

/** Уведомления по откликам: работодателю — новый кандидат, кандидату — приглашение или отказ */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationNotificationService {

    private final ApplicationQueries applications;
    private final ResumeWorkPlaceRepository workPlaces;
    private final CompanyRepository companies;
    private final TelegramService telegram;
    private final ListingButtons buttons;

    @Async
    @Transactional(readOnly = true)
    public void notifyEmployer(Long applicationId) {
        try {
            VacancyResponse r = applications.findFull(applicationId).orElse(null);
            if (r == null) return;
            Resume res = r.getResume();
            String places = workPlaces.findByResumeIdOrderByDisplayOrderAscIdAsc(res.getId()).stream()
                    .limit(3).map(ResumeWorkPlace::getPlace).collect(Collectors.joining(", "));
            telegram.sendMessageWithKeyboard(
                    r.getVacancy().getUser().getTelegramId(),
                    employerText(r.getVacancy().getTitle(), res, places, r.getMessage()),
                    List.of(List.of(webAppButton("👀 Открыть отклик", buttons.openUrl("app_" + r.getId())))));
        } catch (Exception e) {
            log.error("Application {} employer notification failed: {}", applicationId, e.getMessage());
        }
    }

    @Async
    @Transactional(readOnly = true)
    public void notifyCandidate(Long applicationId, boolean invited) {
        try {
            VacancyResponse r = applications.findFull(applicationId).orElse(null);
            if (r == null) return;
            Vacancy v = r.getVacancy();
            String company = companies.findByUserId(v.getUser().getId()).map(Company::getName)
                    .orElse(v.getCompanyName() == null || v.getCompanyName().isBlank() ? "Работодатель" : v.getCompanyName());
            List<List<Map<String, Object>>> rows = new ArrayList<>();
            if (invited) {
                List<Map<String, Object>> contacts = new ArrayList<>();
                String digits = v.getPhone() == null ? "" : v.getPhone().replaceAll("\\D", "");
                if (digits.length() >= 9) {
                    contacts.add(urlButton("💬 WhatsApp", "https://wa.me/" + digits + "?text="
                            + URLEncoder.encode("Здравствуйте! Вы пригласили меня по вакансии «" + v.getTitle() + "» в Work KG",
                            StandardCharsets.UTF_8)));
                }
                String u = v.getUser().getUsername();
                if (u != null && !u.isBlank()) contacts.add(urlButton("✈️ Telegram", "https://t.me/" + u.replaceFirst("^@", "")));
                if (!contacts.isEmpty()) rows.add(contacts);
            }
            rows.add(List.of(webAppButton("📄 Мои отклики", buttons.openUrl("myapps"))));
            telegram.sendMessageWithKeyboard(r.getApplicant().getTelegramId(),
                    candidateText(company, v.getTitle(), invited), rows);
        } catch (Exception e) {
            log.error("Application {} candidate notification failed: {}", applicationId, e.getMessage());
        }
    }

    static String employerText(String vacancyTitle, Resume res, String places, String message) {
        StringBuilder m = new StringBuilder();
        m.append("📨 <b>Новый отклик</b> на «").append(esc(vacancyTitle)).append("»\n\n");
        m.append("👤 ").append(esc(res.getName()));
        if (res.getAge() != null) m.append(", ").append(res.getAge()).append(" лет");
        m.append("\n");
        if (res.getSubcategory() != null) m.append("💼 ").append(esc(res.getSubcategory().getNameRu()));
        if (res.getExperience() != null && res.getExperience() > 0) m.append(" · опыт ").append(res.getExperience()).append(" г.");
        m.append("\n");
        if (places != null && !places.isBlank()) m.append("🏢 Работал(а): ").append(esc(places)).append("\n");
        if (message != null && !message.isBlank()) m.append("\n💬 «").append(esc(message)).append("»\n");
        m.append("\nКонтакты кандидата открыты — он откликнулся сам.");
        return m.toString();
    }

    static String candidateText(String company, String title, boolean invited) {
        return invited
                ? "🎉 <b>" + esc(company) + "</b> приглашает вас по вакансии «" + esc(title) + "»\n\nНапишите работодателю, чтобы договориться о встрече."
                : "По вакансии «" + esc(title) + "» (" + esc(company) + ") выбрали другого кандидата.\n\nНе расстраивайтесь — откликайтесь на другие вакансии в Work KG.";
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
