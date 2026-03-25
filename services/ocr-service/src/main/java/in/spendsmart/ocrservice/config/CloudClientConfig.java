package in.spendsmart.ocrservice.config;

import com.google.cloud.documentai.v1.DocumentProcessorServiceClient;
import com.google.cloud.documentai.v1.DocumentProcessorServiceSettings;
import java.io.IOException;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.textract.TextractClient;

@Configuration
public class CloudClientConfig {

    @Value("${aws.region}")
    private String awsRegion;

    @Value("${aws.endpoint-url:}")
    private String endpointUrl;

    @Bean
    public TextractClient textractClient() {
        var builder = TextractClient.builder().region(Region.of(awsRegion));
        if (StringUtils.hasText(endpointUrl)) {
            builder.endpointOverride(URI.create(endpointUrl));
        }
        return builder.build();
    }

    @Bean
    public SnsClient snsClient() {
        var builder = SnsClient.builder().region(Region.of(awsRegion));
        if (StringUtils.hasText(endpointUrl)) {
            builder.endpointOverride(URI.create(endpointUrl));
        }
        return builder.build();
    }

    @Bean
    public DocumentProcessorServiceClient documentProcessorServiceClient() throws IOException {
        DocumentProcessorServiceSettings settings = DocumentProcessorServiceSettings.newBuilder().build();
        return DocumentProcessorServiceClient.create(settings);
    }
}
