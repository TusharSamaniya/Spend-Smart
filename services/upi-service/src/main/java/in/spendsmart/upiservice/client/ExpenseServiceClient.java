package in.spendsmart.upiservice.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ExpenseServiceClient {

        private final RestClient restClient;

        public ExpenseServiceClient(@Qualifier("expenseRestClient") RestClient restClient) {
                this.restClient = restClient;
        }

    public UUID createFromUpi(
            UUID orgId,
            UUID userId,
            BigDecimal amount,
            String merchantName,
            String merchantVpa,
            LocalDate date,
            String upiRefId
    ) {
        ExpenseCreateResponse response = restClient.post()
                .uri("/v1/expenses/")
                .header("X-Org-Id", orgId.toString())
                .header("X-User-Id", userId.toString())
                .body(new CreateExpenseRequest(amount, "INR", merchantName, merchantVpa, "UPI", date,
                        "UPI Ref: " + upiRefId, null, null, List.of("upi")))
                .retrieve()
                .body(ExpenseCreateResponse.class);

        if (response == null || response.id() == null) {
            throw new IllegalStateException("Expense service response does not contain id");
        }

        return response.id();
    }

    private record CreateExpenseRequest(
            BigDecimal amount,
            String currency,
            String merchantName,
            String merchantVpa,
            String paymentMethod,
            LocalDate expenseDate,
            String notes,
            UUID receiptId,
            UUID projectId,
            List<String> tags
    ) {
    }

    private record ExpenseCreateResponse(UUID id) {
    }
}
