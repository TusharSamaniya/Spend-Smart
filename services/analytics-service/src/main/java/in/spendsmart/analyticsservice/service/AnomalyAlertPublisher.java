package in.spendsmart.analyticsservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnomalyAlertPublisher {

    private final SnsClient snsClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.sns.expense-events-topic-arn:${AWS_SNS_EXPENSE_EVENTS_TOPIC_ARN:arn:aws:sns:ap-south-1:000000000000:expense-events}}")
    private String expenseEventsTopicArn;

    public void publishAnomalies(UUID orgId, List<Anomaly> anomalies) {
        if (anomalies == null || anomalies.isEmpty()) {
            return;
        }

        for (Anomaly anomaly : anomalies) {
            publishAnomaly(orgId, anomaly);
        }
    }

    public void publishAnomaly(UUID orgId, Anomaly anomaly) {
        if (orgId == null || anomaly == null) {
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "analytics.anomaly_detected");
        payload.put("eventTime", OffsetDateTime.now());
        payload.put("orgId", orgId);
        payload.put("category", anomaly.getCategory());
        payload.put("actual", normalize(anomaly.getActual()));
        payload.put("baseline", normalize(anomaly.getBaseline()));
        payload.put("pctAbove", normalize(anomaly.getPctAbove()));

        PublishRequest request = PublishRequest.builder()
                .topicArn(expenseEventsTopicArn)
                .message(toJson(payload))
                .build();

        snsClient.publish(request);
        log.info("Published anomaly alert for orgId={} category={}", orgId, anomaly.getCategory());
    }

    private BigDecimal normalize(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize anomaly event payload", exception);
        }
    }
}
