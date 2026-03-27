package in.spendsmart.notificationservice.controller;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import in.spendsmart.notificationservice.client.WorkflowServiceClient;
import in.spendsmart.notificationservice.client.WorkflowServiceClient.ApprovalActionRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/webhooks/whatsapp")
@RequiredArgsConstructor
public class WebhookController {

    private static final Logger LOG = LoggerFactory.getLogger(WebhookController.class);
    private static final Pattern UUID_PATTERN =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final WorkflowServiceClient workflowServiceClient;

    @Value("${whatsapp.webhook-verify-token:}")
    private String webhookVerifyToken;

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verifyWebhook(
            @RequestParam(name = "hub.challenge", required = false) String challenge,
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String verifyToken) {
        if (!"subscribe".equals(mode) || !StringUtils.hasText(challenge)) {
            return ResponseEntity.badRequest().build();
        }
        if (StringUtils.hasText(webhookVerifyToken) && !webhookVerifyToken.equals(verifyToken)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(challenge);
    }

    @PostMapping
    public ResponseEntity<Void> receiveWebhook(@RequestBody JsonNode payload) {
        try {
            JsonNode entries = payload.path("entry");
            if (!entries.isArray()) {
                return ResponseEntity.ok().build();
            }

            for (JsonNode entry : entries) {
                JsonNode changes = entry.path("changes");
                if (!changes.isArray()) {
                    continue;
                }

                for (JsonNode change : changes) {
                    JsonNode messages = change.path("value").path("messages");
                    if (!messages.isArray()) {
                        continue;
                    }

                    for (JsonNode message : messages) {
                        String fromPhone = message.path("from").asText("");
                        String buttonPayload = extractButtonPayload(message);
                        if (!StringUtils.hasText(buttonPayload)) {
                            continue;
                        }

                        String action = resolveAction(buttonPayload);
                        UUID taskId = extractTaskId(buttonPayload);
                        if (!StringUtils.hasText(action) || taskId == null) {
                            LOG.info("Ignoring WhatsApp webhook payload without actionable task context: {}", buttonPayload);
                            continue;
                        }

                        workflowServiceClient.processApprovalAction(new ApprovalActionRequest(
                                taskId,
                                action,
                                fromPhone,
                                "WHATSAPP_WEBHOOK",
                                buttonPayload
                        ));
                    }
                }
            }
        } catch (Exception ex) {
            LOG.error("Failed to process incoming WhatsApp webhook payload", ex);
        }

        return ResponseEntity.ok().build();
    }

    private String extractButtonPayload(JsonNode message) {
        String interactiveId = message.path("interactive").path("button_reply").path("id").asText("");
        if (StringUtils.hasText(interactiveId)) {
            return interactiveId;
        }
        String interactiveTitle = message.path("interactive").path("button_reply").path("title").asText("");
        if (StringUtils.hasText(interactiveTitle)) {
            return interactiveTitle;
        }
        String legacyPayload = message.path("button").path("payload").asText("");
        if (StringUtils.hasText(legacyPayload)) {
            return legacyPayload;
        }
        return message.path("button").path("text").asText("");
    }

    private String resolveAction(String payload) {
        String normalized = payload.toLowerCase(Locale.ROOT);
        if (normalized.contains("approve")) {
            return "APPROVE";
        }
        if (normalized.contains("reject")) {
            return "REJECT";
        }
        return null;
    }

    private UUID extractTaskId(String payload) {
        Matcher matcher = UUID_PATTERN.matcher(payload);
        if (matcher.find()) {
            return UUID.fromString(matcher.group());
        }
        return null;
    }
}
