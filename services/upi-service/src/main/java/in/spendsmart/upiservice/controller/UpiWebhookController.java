package in.spendsmart.upiservice.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.spendsmart.upiservice.repository.UpiEventRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.HmacUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@RestController
@RequestMapping("/v1/upi")
@RequiredArgsConstructor
public class UpiWebhookController {

    private static final String SIGNATURE_HEADER = "X-Webhook-Signature";

    private final ObjectMapper objectMapper;
    private final UpiEventRepository upiEventRepository;
    private final SqsClient sqsClient;

    @Value("${webhook.signature-secret}")
    private String webhookSignatureSecret;

    @Value("${aws.sqs.queue-url}")
    private String upiEventsQueueUrl;

    @PostMapping("/webhook")
    public ResponseEntity<Void> receiveWebhook(
            @RequestHeader(name = SIGNATURE_HEADER, required = false) String signature,
            @RequestBody String payload
    ) {
        if (!isValidSignature(signature, payload)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String upiRefId;
        try {
            upiRefId = extractUpiRefId(payload);
        } catch (JsonProcessingException exception) {
            return ResponseEntity.badRequest().build();
        }

        if (!StringUtils.hasText(upiRefId)) {
            return ResponseEntity.badRequest().build();
        }

        if (upiEventRepository.existsByUpiRefId(upiRefId)) {
            return ResponseEntity.ok().build();
        }

        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(upiEventsQueueUrl)
                .messageBody(payload)
                .build());

        return ResponseEntity.accepted().build();
    }

    private boolean isValidSignature(String signature, String payload) {
        if (!StringUtils.hasText(signature) || !StringUtils.hasText(webhookSignatureSecret)) {
            return false;
        }

        String normalizedSignature = normalizeSignature(signature);
        String expectedSignature = HmacUtils.hmacSha256Hex(webhookSignatureSecret, payload);

        return MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                normalizedSignature.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String extractUpiRefId(String payload) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(payload);

        String upiRefId = root.path("upi_ref_id").asText("");
        if (StringUtils.hasText(upiRefId)) {
            return upiRefId.trim();
        }

        String camelCaseUpiRefId = root.path("upiRefId").asText("");
        return camelCaseUpiRefId.trim();
    }

    private String normalizeSignature(String signature) {
        String value = signature.trim();
        if (value.regionMatches(true, 0, "sha256=", 0, 7)) {
            return value.substring(7);
        }
        return value;
    }
}
