package job.search.kg.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Выполнить действие после успешного коммита текущей транзакции (или сразу, если транзакции нет).
 * Ошибка действия (например, переполнена очередь рассылок) не должна превращать уже сохранённое
 * объявление в HTTP 500 — пользователь повторит запрос и создаст дубликат. Поэтому только логируем.
 */
@Slf4j
public final class AfterCommit {

    private AfterCommit() {
    }

    public static void run(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safe(action);
                }
            });
        } else {
            safe(action);
        }
    }

    private static void safe(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.error("After-commit action failed: {}", e.getMessage(), e);
        }
    }
}
