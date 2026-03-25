package in.spendsmart.ocrservice.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.spendsmart.ocrservice.entity.Receipt;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReceiptEventPublisher {

    private final SnsClient snsClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.sns.receipt-events-topic-arn}")
    private String receiptEventsTopicArn;

    public void publishOcrCompleted(Receipt receipt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "receipt.ocr_completed");
        payload.put("eventTime", OffsetDateTime.now());
        payload.put("receiptId", receipt.getId());
        payload.put("orgId", receipt.getOrgId());
        payload.put("userId", receipt.getUserId());
        payload.put("amount", receipt.getAmount());
        payload.put("merchantName", receipt.getMerchantName());
        payload.put("receiptDate", receipt.getReceiptDate());
        payload.put("gstAmount", receipt.getGstAmount());
        payload.put("cgst", receipt.getCgst());
        payload.put("sgst", receipt.getSgst());
        payload.put("igst", receipt.getIgst());
        payload.put("currency", receipt.getCurrency());
        payload.put("hsnSacCode", receipt.getHsnSacCode());
        payload.put("lineItems", receipt.getLineItems());
        payload.put("confidenceScore", receipt.getConfidenceScore());
        payload.put("duplicateOf", receipt.getDuplicateOf());
        payload.put("rawOcr", receipt.getRawOcr());

        String message = toJson(payload);
        PublishRequest request = PublishRequest.builder()
                .topicArn(receiptEventsTopicArn)
                .message(message)
                .build();

        snsClient.publish(request);
        log.info("Published receipt.ocr_completed event for receiptId={}", receipt.getId());
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize receipt event payload", exception);
        }
    }
}
