package job.search.kg.exceptions;

/** 409: действие противоречит текущему состоянию; сообщение показывается пользователю как есть */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
