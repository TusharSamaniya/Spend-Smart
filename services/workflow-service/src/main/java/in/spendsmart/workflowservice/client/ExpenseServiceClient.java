package in.spendsmart.workflowservice.client;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ExpenseServiceClient {

    private final RestClient restClient;

    public ExpenseServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${expense.service.url:http://expense-service:8081}") String baseUrl
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(3000);
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public void updateStatus(UUID expenseId, String status) {
        restClient.patch()
                .uri("/v1/expenses/{id}", expenseId)
                .body(Map.of("status", status))
                .retrieve()
                .toBodilessEntity();
    }

    public ExpenseDetails getExpenseDetails(UUID expenseId) {
        ExpenseDetails response = restClient.get()
                .uri("/v1/expenses/{id}", expenseId)
                .retrieve()
                .body(ExpenseDetails.class);

        if (response == null) {
            throw new IllegalStateException("Expense details response was empty for expense " + expenseId);
        }

        return response;
    }

    public record ExpenseDetails(
            UUID expenseId,
            UUID orgId,
            UUID userId,
            BigDecimal amount,
            String currency,
            UUID categoryId,
            UUID teamId,
            String merchantName,
            String status
    ) {
    }
}
