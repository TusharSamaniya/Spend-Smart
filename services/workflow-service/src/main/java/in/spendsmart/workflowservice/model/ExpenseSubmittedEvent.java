package in.spendsmart.workflowservice.model;

import java.math.BigDecimal;
import java.util.UUID;

public record ExpenseSubmittedEvent(
        UUID expenseId,
        UUID orgId,
        UUID userId,
        BigDecimal amount,
        String currency,
        UUID categoryId,
        UUID teamId,
        String merchantName
) {
}
