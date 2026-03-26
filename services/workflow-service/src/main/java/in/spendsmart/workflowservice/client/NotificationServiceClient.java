package in.spendsmart.workflowservice.client;

import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class NotificationServiceClient {

    private final RestClient restClient;

    public NotificationServiceClient(
        RestClient.Builder restClientBuilder,
        @Value("${notification.service.url:http://notification-service:8088}") String baseUrl
    ) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(3000);
    requestFactory.setReadTimeout(3000);
    this.restClient = restClientBuilder
        .baseUrl(baseUrl)
        .requestFactory(requestFactory)
        .build();
    }

    public void sendApprovalRequest(UUID approverId, UUID expenseId) {
    restClient.post()
        .uri("/v1/notifications/approval-request")
        .body(Map.of(
            "approverId", approverId,
            "expenseId", expenseId,
            "message", "Approval requested for expense " + expenseId
        ))
        .retrieve()
        .toBodilessEntity();
    }

    public void sendRejectionNotice(UUID submitterId, UUID expenseId, String reason) {
    restClient.post()
        .uri("/v1/notifications/rejection-notice")
        .body(Map.of(
            "submitterId", submitterId,
            "expenseId", expenseId,
            "reason", reason,
            "message", "Expense " + expenseId + " was rejected: " + reason
        ))
        .retrieve()
        .toBodilessEntity();
    }

    public void sendEscalation(UUID originalApproverId, UUID newApproverId, UUID expenseId) {
    restClient.post()
        .uri("/v1/notifications/escalation")
        .body(Map.of(
            "originalApproverId", originalApproverId,
            "newApproverId", newApproverId,
            "expenseId", expenseId,
            "message", "Expense " + expenseId + " escalated from " + originalApproverId + " to " + newApproverId
        ))
        .retrieve()
        .toBodilessEntity();
    }
}
