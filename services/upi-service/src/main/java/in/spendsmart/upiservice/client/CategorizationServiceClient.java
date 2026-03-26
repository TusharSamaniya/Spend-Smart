package in.spendsmart.upiservice.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CategorizationServiceClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public CategorizationServiceClient(
            @Qualifier("categorizationRestClient") RestClient restClient,
            ObjectMapper objectMapper
    ) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @SneakyThrows
    public CategoryResult categorize(String merchantName, String vpa, BigDecimal amount, String method) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("merchant_name", merchantName);
        payload.put("merchant_vpa", vpa);
        payload.put("amount", amount);
        payload.put("payment_method", method);
        String payloadJson = objectMapper.writeValueAsString(payload);

        CategorizeResponse response = restClient.post()
                .uri("/v1/categorize")
            .contentType(MediaType.APPLICATION_JSON)
                .body(payloadJson)
                .retrieve()
                .body(CategorizeResponse.class);

        if (response == null || response.category() == null) {
            return new CategoryResult(null, "Uncategorized", BigDecimal.ZERO);
        }

        String categoryId = response.category().get("id");
        String categoryName = response.category().getOrDefault("name", "Uncategorized");
        BigDecimal confidence = response.confidence() == null ? BigDecimal.ZERO : response.confidence();

        return new CategoryResult(categoryId, categoryName, confidence);
    }

    private record CategorizeResponse(Map<String, String> category, BigDecimal confidence) {
    }

    public record CategoryResult(String categoryId, String categoryName, BigDecimal confidence) {
    }
}
