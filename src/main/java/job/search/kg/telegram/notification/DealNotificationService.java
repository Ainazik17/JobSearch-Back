package job.search.kg.telegram.notification;

import job.search.kg.entity.Company;
import job.search.kg.entity.Deal;
import job.search.kg.repo.CompanyRepository;
import job.search.kg.repo.DealRepository;
import job.search.kg.service.user.ContactMasking;
import job.search.kg.telegram.TelegramService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static job.search.kg.telegram.TelegramService.webAppButton;

/**
 * Сообщения бота по сделкам (DESIGN_SPEC 8). Ответ — одним касанием в Mini App («Сделки»):
 * кнопка web_app открывает нужный экран. Inline-звёзды прямо в чате потребуют обработчика
 * callback_query в сервисе бота — тексты уже по дизайну.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DealNotificationService {

    private final DealRepository deals;
    private final CompanyRepository companies;
    private final TelegramService telegram;
    private final ListingButtons buttons;

    /** Исполнителю: «Вы работали с X?» */
    @Async
    @Transactional(readOnly = true)
    public void askWorker(Long dealId) {
        deals.findFull(dealId).ifPresent(d -> send(d.getWorker().getTelegramId(),
                "Вы работали с <b>" + esc(employerName(d)) + "</b>?\n💼 " + esc(d.getTitle())
                        + "\n\nПоставьте оценку — это и есть «да» 👇",
                "⭐ Ответить", "deals"));
    }

    /** Исполнителю: работодатель отметил найм */
    @Async
    @Transactional(readOnly = true)
    public void askWorkerToConfirmHire(Long dealId) {
        deals.findFull(dealId).ifPresent(d -> send(d.getWorker().getTelegramId(),
                "<b>" + esc(employerName(d)) + "</b> отметил(а), что вы у них работаете\n💼 " + esc(d.getTitle())
                        + "\n\nВсё верно? Подтвердите — получите <b>+30 баллов</b>.\n<i>Не ответите за 48 ч — засчитаем автоматически.</i>",
                "✓ Подтвердить", "deals"));
    }

    /** Работодателю: исполнитель отметил, что работал */
    @Async
    @Transactional(readOnly = true)
    public void askEmployerToConfirm(Long dealId) {
        deals.findFull(dealId).ifPresent(d -> send(d.getEmployer().getTelegramId(),
                "<b>" + esc(ContactMasking.shortName(d.getWorker().getFirstName(), d.getWorker().getLastName()))
                        + "</b> отметил(а), что работал(а) у вас\n💼 " + esc(d.getTitle())
                        + "\n\nОцените — это подтвердит работу.\n<i>Его оценку вы увидите после ответа. Не ответите за 48 ч — засчитаем автоматически.</i>",
                "⭐ Оценить", "deals_e"));
    }

    private void send(Long chatId, String text, String button, String open) {
        try {
            telegram.sendMessageWithKeyboard(chatId, text,
                    List.of(List.of(webAppButton(button, buttons.openUrl(open)))));
        } catch (Exception e) {
            log.warn("Deal message to {} failed: {}", chatId, e.getMessage());
        }
    }

    private String employerName(Deal d) {
        return companies.findByUserId(d.getEmployer().getId()).map(Company::getName)
                .orElse(ContactMasking.shortName(d.getEmployer().getFirstName(), d.getEmployer().getLastName()));
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
