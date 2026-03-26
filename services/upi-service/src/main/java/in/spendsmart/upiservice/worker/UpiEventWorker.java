package in.spendsmart.upiservice.worker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.spendsmart.upiservice.client.CategorizationServiceClient;
import in.spendsmart.upiservice.client.ExpenseServiceClient;
import in.spendsmart.upiservice.entity.UpiEvent;
import in.spendsmart.upiservice.entity.User;
import in.spendsmart.upiservice.repository.UpiEventRepository;
import in.spendsmart.upiservice.service.UserService;
import in.spendsmart.upiservice.service.VpaResolver;
import io.awspring.cloud.sqs.annotation.SqsListener;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class UpiEventWorker {

    private final UpiEventRepository upiEventRepository;
    private final UserService userService;
    private final VpaResolver vpaResolver;
    private final CategorizationServiceClient categorizationServiceClient;
    private final ExpenseServiceClient expenseServiceClient;
    private final ObjectMapper objectMapper;

    @SqsListener("${aws.sqs.queue-url}")
    public void processUpiEvent(UpiTransactionMessage message) {
        try {
            if (message == null || !StringUtils.hasText(message.upiRefId())) {
                log.warn("Skipping invalid UPI message: {}", message);
                return;
            }

            if (upiEventRepository.existsByUpiRefId(message.upiRefId())) {
                log.info("Skipping duplicate UPI event with upiRefId={}", message.upiRefId());
                return;
            }

            Optional<User> userOptional = userService.findByUpiId(message.vpaSender());
            if (userOptional.isEmpty()) {
                log.warn("No user found for sender VPA={}, upiRefId={}", message.vpaSender(), message.upiRefId());
                return;
            }

            User user = userOptional.get();
            String resolvedMerchant = vpaResolver.resolveMerchant(message.vpaReceiver());
                CategorizationServiceClient.CategoryResult categoryResult = categorizationServiceClient.categorize(
                    resolvedMerchant,
                    message.vpaReceiver(),
                    message.amount(),
                    "UPI"
                );
                LocalDate expenseDate = message.txnTimestamp() == null ? LocalDate.now() : message.txnTimestamp().toLocalDate();
                UUID expenseId = expenseServiceClient.createFromUpi(
                    message.orgId(),
                    user.getId(),
                    message.amount(),
                    resolvedMerchant,
                    message.vpaReceiver(),
                    expenseDate,
                    message.upiRefId()
                );

            UpiEvent upiEvent = UpiEvent.builder()
                    .id(UUID.randomUUID())
                    .orgId(message.orgId())
                    .userId(user.getId())
                    .upiRefId(message.upiRefId())
                    .vpaSender(message.vpaSender())
                    .vpaReceiver(message.vpaReceiver())
                    .amount(message.amount())
                    .currency("INR")
                    .txnTimestamp(message.txnTimestamp())
                    .resolvedMerchant(resolvedMerchant)
                    .expenseId(expenseId)
                    .source(UpiEvent.UpiSource.WEBHOOK)
                    .rawPayload(serializeRawPayload(message, categoryResult))
                    .build();

            upiEventRepository.save(upiEvent);
        } catch (Exception exception) {
            log.error("Failed to process UPI event. Message will be retried by SQS visibility timeout.", exception);
        }
    }

    private String serializeRawPayload(UpiTransactionMessage message, CategorizationServiceClient.CategoryResult categoryResult) {
        try {
            return objectMapper.writeValueAsString(new EnrichedPayload(message, categoryResult));
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private record EnrichedPayload(UpiTransactionMessage message, CategorizationServiceClient.CategoryResult categoryResult) {
    }
}
