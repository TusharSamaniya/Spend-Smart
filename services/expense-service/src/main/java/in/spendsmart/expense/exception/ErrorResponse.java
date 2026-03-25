package in.spendsmart.expense.exception;

public record ErrorResponse(
        String code,
        String message,
        Object details
) {
}
