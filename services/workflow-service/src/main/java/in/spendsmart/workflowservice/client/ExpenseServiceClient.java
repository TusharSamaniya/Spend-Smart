package in.spendsmart.workflowservice.client;

import in.spendsmart.workflowservice.model.ExpenseContext;
import java.util.Optional;
import java.util.UUID;

public interface ExpenseServiceClient {

    void updateStatus(UUID expenseId, ExpenseStatus status);

    Optional<ExpenseContext> getExpenseContext(UUID expenseId);

    enum ExpenseStatus {
        PENDING_APPROVAL,
        APPROVED,
        REJECTED
    }
}
