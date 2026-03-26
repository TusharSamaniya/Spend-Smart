package in.spendsmart.upiservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ClientConfig {

    @Bean("categorizationRestClient")
    public RestClient categorizationRestClient() {
        return RestClient.builder()
                .baseUrl("http://categorization-service:8083")
                .build();
    }

    @Bean("expenseRestClient")
    public RestClient expenseRestClient() {
        return RestClient.builder()
                .baseUrl("http://expense-service:8081")
                .build();
    }
}
