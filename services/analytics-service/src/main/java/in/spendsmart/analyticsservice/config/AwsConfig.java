package in.spendsmart.analyticsservice.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;

@Configuration
public class AwsConfig {

    @Bean
    public SnsClient snsClient(
            @Value("${aws.region}") String region,
            @Value("${aws.endpoint-url:}") String endpointUrl,
            @Value("${AWS_ACCESS_KEY_ID:test}") String accessKeyId,
            @Value("${AWS_SECRET_ACCESS_KEY:test}") String secretAccessKey
    ) {
        var builder = SnsClient.builder()
                .region(Region.of(region))
                .credentialsProvider(
                        StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey))
                );

        if (StringUtils.hasText(endpointUrl)) {
            builder.endpointOverride(URI.create(endpointUrl));
        }

        return builder.build();
    }
}
