package in.spendsmart.ocrservice.config;

import com.google.cloud.documentai.v1.DocumentProcessorServiceClient;
import com.google.cloud.documentai.v1.DocumentProcessorServiceSettings;
import com.google.api.gax.core.NoCredentialsProvider;
import java.io.IOException;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.textract.TextractClient;

@Configuration
public class CloudClientConfig {

    @Value("${aws.region}")
    private String awsRegion;

    @Value("${aws.endpoint-url:}")
    private String endpointUrl;

    @Value("${AWS_ACCESS_KEY_ID:test}")
    private String accessKeyId;

    @Value("${AWS_SECRET_ACCESS_KEY:test}")
    private String secretAccessKey;

    @Bean
    public TextractClient textractClient() {
        var builder = TextractClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(staticCredentialsProvider());
        if (StringUtils.hasText(endpointUrl)) {
            builder.endpointOverride(URI.create(endpointUrl));
        }
        return builder.build();
    }

    @Bean
    public SnsClient snsClient() {
        var builder = SnsClient.builder().region(Region.of(awsRegion));
        builder.credentialsProvider(staticCredentialsProvider());
        if (StringUtils.hasText(endpointUrl)) {
            builder.endpointOverride(URI.create(endpointUrl));
        }
        return builder.build();
    }

    @Bean
    public SqsAsyncClient sqsAsyncClient() {
        var builder = SqsAsyncClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(staticCredentialsProvider());
        if (StringUtils.hasText(endpointUrl)) {
            builder.endpointOverride(URI.create(endpointUrl));
        }
        return builder.build();
    }

    @Bean
    public DocumentProcessorServiceClient documentProcessorServiceClient() throws IOException {
        DocumentProcessorServiceSettings settings = DocumentProcessorServiceSettings.newBuilder()
                .setCredentialsProvider(NoCredentialsProvider.create())
                .build();
        return DocumentProcessorServiceClient.create(settings);
    }

    private StaticCredentialsProvider staticCredentialsProvider() {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey));
    }
}
