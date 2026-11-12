package job.search.kg.telegram.notification;

import job.search.kg.entity.Company;
import job.search.kg.entity.CompanyFollower;
import job.search.kg.entity.Resume;
import job.search.kg.entity.User;
import job.search.kg.entity.Vacancy;
import job.search.kg.repo.CompanyFollowerRepository;
import job.search.kg.repo.CompanyRepository;
import job.search.kg.repo.ResumeRepository;
import job.search.kg.repo.VacancyRepository;
import job.search.kg.service.user.BotSubscriptionService;
import job.search.kg.telegram.TelegramService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class VacancyNotificationService {

    private final ResumeRepository resumeRepository;
    private final VacancyRepository vacancyRepository;
    private final ListingButtons listingButtons;
    private final TelegramService telegramService;
    private final BotSubscriptionService botSubscriptionService;
    private final CompanyRepository companyRepository;
    private final CompanyFollowerRepository companyFollowerRepository;

    /**
     * Отправить уведомления соискателям о новой вакансии.
     * Уведомляем тех, у кого есть активное резюме в той же подкатегории и городе.
     * Принимает id, а не сущность: метод асинхронный и вызывается после коммита,
     * поэтому вакансию перечитываем в своей транзакции (иначе ленивые поля не загрузятся).
     */
    @Async
    @Transactional(readOnly = true)
    public void notifyUsersAboutNewVacancy(Long vacancyId) {
        try {
            Vacancy vacancy = vacancyRepository.findByIdWithUser(vacancyId).orElse(null);
            if (vacancy == null || !Boolean.TRUE.equals(vacancy.getIsActive())) return;

            // Сначала подписчики компании («Подписаться на вакансии»): им — отдельный текст,
            // и в рассылке по категории они второй раз не получат ту же вакансию
            Set<Long> notifiedUserIds = new HashSet<>(notifyCompanyFollowers(vacancy));

            if (vacancy.getSubcategory() == null) {
                log.info("Vacancy {} has no subcategory — category notifications skipped", vacancyId);
                return;
            }

            List<Resume> matchingResumes = resumeRepository
                    .findBySubcategoryIdAndCityIdAndIsActiveTrue(
                            vacancy.getSubcategory().getId(),
                            vacancy.getCity().getId()
                    );

            for (Resume resume : matchingResumes) {
                User user = resume.getUser();

                // Пропускаем автора вакансии
                if (user.getTelegramId().equals(vacancy.getUser().getTelegramId())) {
                    continue;
                }

                if (notifiedUserIds.contains(user.getTelegramId())) {
                    continue;
                }

                if (Boolean.TRUE.equals(user.getIsBanned())) {
                    continue;
                }

                // Отключил «Новые вакансии» в настройках уведомлений
                if (Boolean.FALSE.equals(user.getNotifyVacancies())) {
                    continue;
                }

                boolean hasSubscription = isPro(user.getTelegramId());

                String message = buildNotificationMessage(vacancy, user.getLanguage(), hasSubscription)
                        + voiceLine(vacancy.getVoiceUrl(), user.getLanguage());

                telegramService.sendMessageWithKeyboard(
                        user.getTelegramId(),
                        message,
                        listingButtons.build(
                                ListingButtons.Kind.VACANCY,
                                vacancy.getId(),
                                hasSubscription,
                                vacancy.getPhone(),
                                vacancy.getUser().getUsername(),
                                "Здравствуйте! Пишу по вакансии «" + vacancy.getTitle() + "» из Work KG",
                                user.getLanguage()
                        )
                );
                notifiedUserIds.add(user.getTelegramId());

                log.info("Notification sent to user {} about new vacancy {}",
                        user.getTelegramId(), vacancy.getId());
            }

            log.info("Notified {} users about new vacancy in {} - {}",
                    notifiedUserIds.size(),
                    vacancy.getCity().getNameRu(),
                    vacancy.getSubcategory().getNameRu());

        } catch (Exception e) {
            log.error("Error notifying users about new vacancy {}: {}",
                    vacancyId, e.getMessage(), e);
        }
    }

    /** @return telegramId тех, кому отправили */
    private Set<Long> notifyCompanyFollowers(Vacancy vacancy) {
        Set<Long> sent = new HashSet<>();
        Company company = companyRepository.findByUserId(vacancy.getUser().getId()).orElse(null);
        if (company == null) return sent;
        for (CompanyFollower f : companyFollowerRepository.findByCompanyIdWithUser(company.getId())) {
            User user = f.getUser();
            if (user.getTelegramId().equals(vacancy.getUser().getTelegramId())) continue;
            if (Boolean.TRUE.equals(user.getIsBanned()) || Boolean.FALSE.equals(user.getNotifyCompanies())) continue;
            if (!sent.add(user.getTelegramId())) continue;
            boolean pro = isPro(user.getTelegramId());
            telegramService.sendMessageWithKeyboard(
                    user.getTelegramId(),
                    buildFollowerMessage(company, vacancy, user.getLanguage())
                            + voiceLine(vacancy.getVoiceUrl(), user.getLanguage()),
                    listingButtons.build(ListingButtons.Kind.VACANCY, vacancy.getId(), pro,
                            vacancy.getPhone(), vacancy.getUser().getUsername(),
                            "Здравствуйте! Пишу по вакансии «" + vacancy.getTitle() + "» из Work KG",
                            user.getLanguage()));
        }
        log.info("Notified {} followers of company {} about vacancy {}", sent.size(), company.getId(), vacancy.getId());
        return sent;
    }

    static String buildFollowerMessage(Company company, Vacancy vacancy, User.Language language) {
        String name = escape(company.getName());
        String title = escape(vacancy.getTitle());
        String salary = vacancy.getSalary() == null || vacancy.getSalary().isBlank() ? "" : "\n💰 " + escape(vacancy.getSalary());
        return switch (language) {
            case KY -> "🏢 <b>" + name + "</b> жаңы вакансия жарыялады\n\n📋 " + title + salary
                    + "\n\nСиз бул компанияга жазылгансыз.";
            case EN -> "🏢 <b>" + name + "</b> posted a new vacancy\n\n📋 " + title + salary
                    + "\n\nYou follow this company.";
            default -> "🏢 <b>" + name + "</b> открыл(а) новую вакансию\n\n📋 " + title + salary
                    + "\n\nВы подписаны на эту компанию.";
        };
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String buildNotificationMessage(Vacancy vacancy, User.Language language, boolean hasSubscription) {
        String subcategoryName = getSubcategoryName(vacancy, language);
        String cityName = getCityName(vacancy, language);
        String title = vacancy.getTitle();
        String salary = vacancy.getSalary() != null ? vacancy.getSalary() : "";
        String phone = vacancy.getPhone();

        return switch (language) {
            case KY -> buildKyrgyzMessage(subcategoryName, cityName, title, salary, phone, hasSubscription);
            case EN -> buildEnglishMessage(subcategoryName, cityName, title, salary, phone, hasSubscription);
            default -> buildRussianMessage(subcategoryName, cityName, title, salary, phone, hasSubscription);
        };
    }

    private String buildRussianMessage(String subcategory, String city, String title,
                                       String salary, String phone, boolean hasSubscription) {
        StringBuilder msg = new StringBuilder();
        msg.append("🔔 <b>Новая вакансия!</b>\n\n");
        msg.append("📋 ").append(title).append("\n");
        msg.append("📂 ").append(subcategory).append("\n");
        msg.append("🏙 ").append(city).append("\n");

        if (!salary.isEmpty()) {
            msg.append("💰 ").append(salary).append("\n");
        }

        if (hasSubscription && phone != null && !phone.isEmpty()) {
            msg.append("📞 ").append(phone).append("\n");
            msg.append("\nДобавлена новая вакансия по категории <b>").append(subcategory)
                    .append("</b> в вашем городе!\n\n");
            msg.append("⚡️ Свяжитесь с работодателем прямо сейчас!");
        } else {
            msg.append("📞 <i>Чтобы увидеть контакт — приобретите подписку быстрее!</i>\n");
            msg.append("\nДобавлена новая вакансия по категории <b>").append(subcategory)
                    .append("</b> в вашем городе!\n\n");
            msg.append("⚡️ Скорее откройте наше приложение и приобретите подписку!");
        }

        return msg.toString();
    }

    private String buildKyrgyzMessage(String subcategory, String city, String title,
                                      String salary, String phone, boolean hasSubscription) {
        StringBuilder msg = new StringBuilder();
        msg.append("🔔 <b>Жаңы вакансия!</b>\n\n");
        msg.append("📋 ").append(title).append("\n");
        msg.append("📂 ").append(subcategory).append("\n");
        msg.append("🏙 ").append(city).append("\n");

        if (!salary.isEmpty()) {
            msg.append("💰 ").append(salary).append("\n");
        }

        if (hasSubscription && phone != null && !phone.isEmpty()) {
            msg.append("📞 ").append(phone).append("\n");
            msg.append("\n<b>").append(subcategory)
                    .append("</b> категориясы боюнча сиздин шаарыңызда жаңы вакансия кошулду!\n\n");
            msg.append("⚡️ Азыр эле иш берүүчү менен байланышыңыз!");
        } else {
            msg.append("📞 <i>Байланышты көрүү үчүн — жазылууну тезирээк сатып алыңыз!</i>\n");
            msg.append("\n<b>").append(subcategory)
                    .append("</b> категориясы боюнча сиздин шаарыңызда жаңы вакансия кошулду!\n\n");
            msg.append("⚡️ Тездик менен биздин тиркемени ачып, жазылуу алыңыз!");
        }

        return msg.toString();
    }

    private String buildEnglishMessage(String subcategory, String city, String title,
                                       String salary, String phone, boolean hasSubscription) {
        StringBuilder msg = new StringBuilder();
        msg.append("🔔 <b>New Vacancy!</b>\n\n");
        msg.append("📋 ").append(title).append("\n");
        msg.append("📂 ").append(subcategory).append("\n");
        msg.append("🏙 ").append(city).append("\n");

        if (!salary.isEmpty()) {
            msg.append("💰 ").append(salary).append("\n");
        }

        if (hasSubscription && phone != null && !phone.isEmpty()) {
            msg.append("📞 ").append(phone).append("\n");
            msg.append("\nA new vacancy in <b>").append(subcategory)
                    .append("</b> category has been added in your city!\n\n");
            msg.append("⚡️ Contact the employer right now!");
        } else {
            msg.append("📞 <i>To see the contact — get a subscription faster!</i>\n");
            msg.append("\nA new vacancy in <b>").append(subcategory)
                    .append("</b> category has been added in your city!\n\n");
            msg.append("⚡️ Open our app now and get a subscription!");
        }

        return msg.toString();
    }
    private String getSubcategoryName(Vacancy vacancy, User.Language language) {
        return switch (language) {
            case KY -> vacancy.getSubcategory().getNameKy() != null ?
                    vacancy.getSubcategory().getNameKy() : vacancy.getSubcategory().getNameRu();
            case EN -> vacancy.getSubcategory().getNameEn() != null ?
                    vacancy.getSubcategory().getNameEn() : vacancy.getSubcategory().getNameRu();
            default -> vacancy.getSubcategory().getNameRu();
        };
    }

    private String getCityName(Vacancy vacancy, User.Language language) {
        return switch (language) {
            case EN -> vacancy.getCity().getNameEn() != null ?
                    vacancy.getCity().getNameEn() : vacancy.getCity().getNameRu();
            default -> vacancy.getCity().getNameRu();
        };
    }

    /** Проверка PRO не должна обрывать рассылку остальным получателям */
    private boolean isPro(Long telegramId) {
        try {
            return botSubscriptionService.hasActiveSubscription(telegramId);
        } catch (Exception e) {
            log.warn("PRO check failed for {}: {}", telegramId, e.getMessage());
            return false;
        }
    }

    /** Строка «есть голосовое» — само аудио слушают в карточке («Подробнее») */
    static String voiceLine(String voiceUrl, User.Language lang) {
        if (voiceUrl == null || voiceUrl.isBlank()) return "";
        return switch (lang) {
            case KY -> "\n\n🎧 Үн билдирүүсү бар — «Толугураак» басып угуңуз";
            case EN -> "\n\n🎧 Voice note attached — tap «Details» to listen";
            default -> "\n\n🎧 Есть голосовое — послушайте в «Подробнее»";
        };
    }
}
