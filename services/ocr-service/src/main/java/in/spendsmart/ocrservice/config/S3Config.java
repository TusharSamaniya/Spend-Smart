package in.spendsmart.ocrservice.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.S3Presigner.Builder;

@Configuration
public class S3Config {

    @Value("${aws.region}")
    private String awsRegion;

    @Value("${aws.endpoint-url:}")
    private String endpointUrl;

    @Bean
    public S3Client s3Client() {
        return configureS3ClientBuilder().build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return configureS3PresignerBuilder().build();
    }

    private S3ClientBuilder configureS3ClientBuilder() {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(awsRegion))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        if (StringUtils.hasText(endpointUrl)) {
            builder.endpointOverride(URI.create(endpointUrl));
        }
        return builder;
    }

    private Builder configureS3PresignerBuilder() {
        Builder builder = S3Presigner.builder()
                .region(Region.of(awsRegion))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        if (StringUtils.hasText(endpointUrl)) {
            builder.endpointOverride(URI.create(endpointUrl));
        }
        return builder;
    }
}
