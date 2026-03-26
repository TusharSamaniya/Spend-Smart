package in.spendsmart.upiservice.client;

import java.math.BigDecimal;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class CategorizationServiceClient {

    private final @Qualifier("categorizationRestClient") RestClient restClient;

    public CategoryResult categorize(String merchantName, String vpa, BigDecimal amount, String method) {
        CategorizeResponse response = restClient.post()
                .uri("/v1/categorize")
                .body(new CategorizeRequest(merchantName, vpa, amount, method))
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

    private record CategorizeRequest(String merchant_name, String merchant_vpa, BigDecimal amount, String payment_method) {
    }

    private record CategorizeResponse(Map<String, String> category, BigDecimal confidence) {
    }

    public record CategoryResult(String categoryId, String categoryName, BigDecimal confidence) {
    }
}
