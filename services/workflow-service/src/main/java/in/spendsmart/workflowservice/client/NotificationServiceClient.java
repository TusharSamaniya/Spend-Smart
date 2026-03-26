package in.spendsmart.workflowservice.client;

import java.util.UUID;

public interface NotificationServiceClient {

    void sendApprovalRequest(UUID approverId, Object expense);

    void notifyExpenseRejected(UUID expenseId, String comment);
}
