package in.spendsmart.upiservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;

@Configuration
public class ClientConfig {

    @Bean("categorizationRestClient")
        public RestClient categorizationRestClient(
            @Value("${clients.categorization.base-url:http://categorization-service:8083}") String baseUrl
        ) {
        return RestClient.builder()
            .baseUrl(baseUrl)
                .build();
    }

    @Bean("expenseRestClient")
        public RestClient expenseRestClient(
            @Value("${clients.expense.base-url:http://expense-service:8081}") String baseUrl
        ) {
        return RestClient.builder()
            .baseUrl(baseUrl)
                .build();
    }
}
