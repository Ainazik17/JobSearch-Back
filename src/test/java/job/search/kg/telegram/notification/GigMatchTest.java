package job.search.kg.telegram.notification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GigMatchTest {

    @Test
    void matchesByWordStem() {
        assertTrue(GigNotificationService.matches("Грузчики на переезд", "Грузчик|Курьер"));
        assertTrue(GigNotificationService.matches("Нужна няня на вечер", "Няня"));
        assertTrue(GigNotificationService.matches("Сантехник: течёт кран", "Сантехник"));
        // в обе стороны: короткое слово в задании, длинное в услугах
        assertTrue(GigNotificationService.matches("Нужен электрик", "Электрик-монтажник"));
    }

    @Test
    void matchesBySynonyms() {
        assertTrue(GigNotificationService.matches("Посидеть с ребёнком вечером", "Няня"));
        assertTrue(GigNotificationService.matches("Помыть окна в офисе", "Уборщица"));
        assertTrue(GigNotificationService.matches("Помочь с переездом", "Грузчик"));
        assertTrue(GigNotificationService.matches("Собрать шкаф-купе", "Сборщик мебели|Сантехник"));
        assertTrue(GigNotificationService.matches("Отвезти документы в ЦУМ", "Курьер"));
        assertTrue(GigNotificationService.matches("Повесить полку и карниз", "Мастер на час"));
    }

    @Test
    void doesNotMatchUnrelated() {
        assertFalse(GigNotificationService.matches("Грузчики на переезд", ""));
        assertFalse(GigNotificationService.matches("Грузчики на переезд", "IT"));
        assertFalse(GigNotificationService.matches("Официант на банкет", "Репетитор|Няня"));
        assertFalse(GigNotificationService.matches("Повесить полку", "Мастер маникюра"));
        assertFalse(GigNotificationService.matches("Помыть окна", "Грузчик|Курьер"));
    }
}
