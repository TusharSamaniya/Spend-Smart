package in.spendsmart.notificationservice.worker;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.spendsmart.notificationservice.channel.EmailService;
import in.spendsmart.notificationservice.channel.PushNotificationService;
import in.spendsmart.notificationservice.channel.WhatsAppService;
import in.spendsmart.notificationservice.service.UserPreferenceService;
import in.spendsmart.notificationservice.service.UserPreferenceService.Channel;
import in.spendsmart.notificationservice.service.UserPreferenceService.ContactInfo;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${aws.sqs.notifications-queue-url:}')")
public class NotificationWorker {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationWorker.class);

    private final EmailService emailService;
    private final WhatsAppService whatsAppService;
    private final PushNotificationService pushNotificationService;
    private final UserPreferenceService userPreferenceService;
    private final ObjectMapper objectMapper;

    @SqsListener("${aws.sqs.notifications-queue-url}")
    public void handleNotification(String messageBody) {
        try {
            NotificationMessage message = objectMapper.readValue(messageBody, NotificationMessage.class);
            String event = message.event();

            switch (event) {
                case "approval.requested" -> sendApprovalNotifications(message);
                case "expense.approved" -> sendApprovalConfirmation(message);
                case "expense.rejected" -> sendRejectionNotifications(message);
                case "budget.threshold_reached" -> sendBudgetAlert(message);
                case "upi.expense_captured" -> sendUpiCaptureNotification(message);
                case "expense.escalated" -> sendEscalationNotification(message);
                default -> LOG.warn("Unsupported notification event: {}", event);
            }
        } catch (Exception ex) {
            LOG.error("Failed to process notification message: {}", messageBody, ex);
        }
    }

    private void sendApprovalNotifications(NotificationMessage message) {
        ContactInfo contactInfo = userPreferenceService.getContactInfo(message.recipientId());
        Set<Channel> channels = userPreferenceService.getPreferredChannels(message.recipientId(), message.event());
        Map<String, Object> payload = message.payload();

        String approverName = asString(payload, "approverName", "Approver");
        String employeeName = asString(payload, "employeeName", "Employee");
        BigDecimal amount = asBigDecimal(payload, "amount", BigDecimal.ZERO);
        String merchantName = asString(payload, "merchantName", "Unknown merchant");
        UUID taskId = asUuid(payload, "taskId", UUID.randomUUID());

        if (channels.contains(Channel.EMAIL) && hasText(contactInfo.email())) {
            emailService.sendApprovalRequest(contactInfo.email(), approverName, employeeName, amount, merchantName, taskId);
        }
        if (channels.contains(Channel.WHATSAPP) && hasText(contactInfo.phone())) {
            whatsAppService.sendApprovalRequest(contactInfo.phone(), approverName, employeeName, amount, merchantName);
        }
        if (channels.contains(Channel.PUSH) && hasText(contactInfo.deviceToken())) {
            pushNotificationService.sendPush(
                    contactInfo.deviceToken(),
                    "Approval requested",
                    employeeName + " submitted " + amount + " at " + merchantName
            );
        }
    }

    private void sendApprovalConfirmation(NotificationMessage message) {
        ContactInfo contactInfo = userPreferenceService.getContactInfo(message.recipientId());
        Set<Channel> channels = userPreferenceService.getPreferredChannels(message.recipientId(), message.event());
        Map<String, Object> payload = message.payload();

        String employeeName = asString(payload, "employeeName", "Employee");
        BigDecimal amount = asBigDecimal(payload, "amount", BigDecimal.ZERO);
        String subject = "Expense approved";
        String emailBody = "<p>Hello " + employeeName + ", your expense of <strong>" + amount + "</strong> has been approved.</p>";

        if (channels.contains(Channel.EMAIL) && hasText(contactInfo.email())) {
            emailService.sendEmail(contactInfo.email(), subject, emailBody);
        }
        if (channels.contains(Channel.WHATSAPP) && hasText(contactInfo.phone())) {
            whatsAppService.sendTemplateMessage(
                    contactInfo.phone(),
                    "expense_approved",
                    java.util.List.of(employeeName, amount)
            );
        }
        if (channels.contains(Channel.PUSH) && hasText(contactInfo.deviceToken())) {
            pushNotificationService.sendPush(contactInfo.deviceToken(), "Expense approved", "Your expense has been approved.");
        }
    }

    private void sendRejectionNotifications(NotificationMessage message) {
        ContactInfo contactInfo = userPreferenceService.getContactInfo(message.recipientId());
        Set<Channel> channels = userPreferenceService.getPreferredChannels(message.recipientId(), message.event());
        Map<String, Object> payload = message.payload();

        String employeeName = asString(payload, "employeeName", "Employee");
        BigDecimal amount = asBigDecimal(payload, "amount", BigDecimal.ZERO);
        String reason = asString(payload, "reason", "Not specified");

        if (channels.contains(Channel.EMAIL) && hasText(contactInfo.email())) {
            emailService.sendRejectionNotice(contactInfo.email(), employeeName, amount, reason);
        }
        if (channels.contains(Channel.WHATSAPP) && hasText(contactInfo.phone())) {
            whatsAppService.sendRejectionNotice(contactInfo.phone(), employeeName, reason);
        }
        if (channels.contains(Channel.PUSH) && hasText(contactInfo.deviceToken())) {
            pushNotificationService.sendPush(contactInfo.deviceToken(), "Expense rejected", "Reason: " + reason);
        }
    }

    private void sendBudgetAlert(NotificationMessage message) {
        ContactInfo contactInfo = userPreferenceService.getContactInfo(message.recipientId());
        Set<Channel> channels = userPreferenceService.getPreferredChannels(message.recipientId(), message.event());
        Map<String, Object> payload = message.payload();

        String managerName = asString(payload, "managerName", "Manager");
        String budgetName = asString(payload, "budgetName", "Budget");
        BigDecimal pctUsed = asBigDecimal(payload, "pctUsed", BigDecimal.ZERO);

        if (channels.contains(Channel.EMAIL) && hasText(contactInfo.email())) {
            emailService.sendBudgetAlert(contactInfo.email(), managerName, budgetName, pctUsed);
        }
        if (channels.contains(Channel.WHATSAPP) && hasText(contactInfo.phone())) {
            whatsAppService.sendTemplateMessage(
                    contactInfo.phone(),
                    "budget_threshold_reached",
                    java.util.List.of(managerName, budgetName, pctUsed)
            );
        }
        if (channels.contains(Channel.PUSH) && hasText(contactInfo.deviceToken())) {
            pushNotificationService.sendPush(contactInfo.deviceToken(), "Budget alert", budgetName + " usage is at " + pctUsed + "%");
        }
    }

    private void sendUpiCaptureNotification(NotificationMessage message) {
        ContactInfo contactInfo = userPreferenceService.getContactInfo(message.recipientId());
        Set<Channel> channels = userPreferenceService.getPreferredChannels(message.recipientId(), message.event());
        Map<String, Object> payload = message.payload();

        BigDecimal amount = asBigDecimal(payload, "amount", BigDecimal.ZERO);
        String merchantName = asString(payload, "merchantName", "merchant");
        String topCategory = asString(payload, "category", "General");

        if (channels.contains(Channel.EMAIL) && hasText(contactInfo.email())) {
            String body = "<p>UPI expense captured: <strong>" + amount + "</strong> at " + merchantName + ".</p>";
            emailService.sendEmail(contactInfo.email(), "UPI expense captured", body);
        }
        if (channels.contains(Channel.WHATSAPP) && hasText(contactInfo.phone())) {
            whatsAppService.sendDailySpendSummary(contactInfo.phone(), amount, topCategory);
        }
        if (channels.contains(Channel.PUSH) && hasText(contactInfo.deviceToken())) {
            pushNotificationService.sendPush(contactInfo.deviceToken(), "UPI expense captured", amount + " at " + merchantName);
        }
    }

    private void sendEscalationNotification(NotificationMessage message) {
        ContactInfo contactInfo = userPreferenceService.getContactInfo(message.recipientId());
        Set<Channel> channels = userPreferenceService.getPreferredChannels(message.recipientId(), message.event());
        Map<String, Object> payload = message.payload();

        String escalationReason = asString(payload, "reason", "Escalation triggered");
        String subject = "Expense escalation";

        if (channels.contains(Channel.EMAIL) && hasText(contactInfo.email())) {
            emailService.sendEmail(contactInfo.email(), subject, "<p>Expense has been escalated: " + escalationReason + "</p>");
        }
        if (channels.contains(Channel.WHATSAPP) && hasText(contactInfo.phone())) {
            whatsAppService.sendTemplateMessage(contactInfo.phone(), "expense_escalated", java.util.List.of(escalationReason));
        }
        if (channels.contains(Channel.PUSH) && hasText(contactInfo.deviceToken())) {
            pushNotificationService.sendPush(contactInfo.deviceToken(), subject, escalationReason);
        }
    }

    private String asString(Map<String, Object> payload, String key, String defaultValue) {
        Object value = payload.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private BigDecimal asBigDecimal(Map<String, Object> payload, String key, BigDecimal defaultValue) {
        Object value = payload.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private UUID asUuid(Map<String, Object> payload, String key, UUID defaultValue) {
        Object value = payload.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            return defaultValue;
        }
    }

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }

    public record NotificationMessage(String event, UUID recipientId, Map<String, Object> payload) {
    }
}
