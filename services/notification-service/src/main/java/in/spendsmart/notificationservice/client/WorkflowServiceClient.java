package in.spendsmart.notificationservice.client;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WorkflowServiceClient {

    private final RestClient restClient;

    public WorkflowServiceClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl("http://workflow-service:8086").build();
    }

    public void processApprovalAction(ApprovalActionRequest request) {
        restClient.post()
                .uri("/v1/workflow/approval-action")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    public record ApprovalActionRequest(
            UUID taskId,
            String action,
            String actorPhone,
            String source,
            String rawPayload) {
    }
}
