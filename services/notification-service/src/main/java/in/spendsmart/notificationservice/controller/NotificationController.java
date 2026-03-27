package in.spendsmart.notificationservice.controller;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import in.spendsmart.notificationservice.channel.EmailService;
import in.spendsmart.notificationservice.channel.PushNotificationService;
import in.spendsmart.notificationservice.channel.WhatsAppService;
import in.spendsmart.notificationservice.service.UserPreferenceService;
import in.spendsmart.notificationservice.service.UserPreferenceService.Channel;
import in.spendsmart.notificationservice.service.UserPreferenceService.ContactInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationController.class);

    private final EmailService emailService;
    private final WhatsAppService whatsAppService;
    private final PushNotificationService pushNotificationService;
    private final UserPreferenceService userPreferenceService;

    @PostMapping("/approval-request")
    public ResponseEntity<Void> sendApprovalRequest(@Valid @RequestBody ApprovalRequest request) {
        try {
            ContactInfo contactInfo = userPreferenceService.getContactInfo(request.approverId());
            Set<Channel> channels = userPreferenceService.getPreferredChannels(request.approverId(), "approval.requested");

            if (channels.contains(Channel.EMAIL) && hasText(contactInfo.email())) {
                emailService.sendApprovalRequest(
                        contactInfo.email(),
                        "Approver",
                        request.employeeName(),
                        request.amount(),
                        request.merchantName(),
                        request.expenseId());
            }
            if (channels.contains(Channel.WHATSAPP) && hasText(contactInfo.phone())) {
                whatsAppService.sendApprovalRequest(
                        contactInfo.phone(),
                        "Approver",
                        request.employeeName(),
                        request.amount(),
                        request.merchantName());
            }
            if (channels.contains(Channel.PUSH) && hasText(contactInfo.deviceToken())) {
                pushNotificationService.sendPush(
                        contactInfo.deviceToken(),
                        "Approval requested",
                        request.employeeName() + " submitted " + request.amount() + " at " + request.merchantName());
            }

            return ResponseEntity.accepted().build();
        } catch (Exception ex) {
            LOG.error("Failed to send approval-request notification", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/rejection")
    public ResponseEntity<Void> sendRejection(@Valid @RequestBody RejectionRequest request) {
        try {
            ContactInfo contactInfo = userPreferenceService.getContactInfo(request.submitterId());
            Set<Channel> channels = userPreferenceService.getPreferredChannels(request.submitterId(), "expense.rejected");

            if (channels.contains(Channel.EMAIL) && hasText(contactInfo.email())) {
                emailService.sendRejectionNotice(contactInfo.email(), "Employee", request.amount(), request.reason());
            }
            if (channels.contains(Channel.WHATSAPP) && hasText(contactInfo.phone())) {
                whatsAppService.sendRejectionNotice(contactInfo.phone(), "Employee", request.reason());
            }
            if (channels.contains(Channel.PUSH) && hasText(contactInfo.deviceToken())) {
                pushNotificationService.sendPush(
                        contactInfo.deviceToken(),
                        "Expense rejected",
                        "Expense " + request.expenseId() + " rejected. Reason: " + request.reason());
            }

            return ResponseEntity.accepted().build();
        } catch (Exception ex) {
            LOG.error("Failed to send rejection notification", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/escalation")
    public ResponseEntity<Void> sendEscalation(@Valid @RequestBody EscalationRequest request) {
        try {
            ContactInfo contactInfo = userPreferenceService.getContactInfo(request.newApproverId());
            Set<Channel> channels = userPreferenceService.getPreferredChannels(request.newApproverId(), "expense.escalated");

            String escalationMessage = "Expense " + request.expenseId() + " has been escalated from approver "
                    + request.originalApproverId() + " to you.";

            if (channels.contains(Channel.EMAIL) && hasText(contactInfo.email())) {
                emailService.sendEmail(contactInfo.email(), "Expense escalation", "<p>" + escalationMessage + "</p>");
            }
            if (channels.contains(Channel.WHATSAPP) && hasText(contactInfo.phone())) {
                whatsAppService.sendTemplateMessage(contactInfo.phone(), "expense_escalated", java.util.List.of(request.expenseId()));
            }
            if (channels.contains(Channel.PUSH) && hasText(contactInfo.deviceToken())) {
                pushNotificationService.sendPush(contactInfo.deviceToken(), "Expense escalation", escalationMessage);
            }

            return ResponseEntity.accepted().build();
        } catch (Exception ex) {
            LOG.error("Failed to send escalation notification", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }

    public record ApprovalRequest(
            @NotNull UUID approverId,
            @NotNull UUID expenseId,
            @NotBlank String employeeName,
            @NotNull BigDecimal amount,
            @NotBlank String merchantName) {
    }

    public record RejectionRequest(
            @NotNull UUID submitterId,
            @NotNull UUID expenseId,
            @NotNull BigDecimal amount,
            @NotBlank String reason) {
    }

    public record EscalationRequest(
            @NotNull UUID originalApproverId,
            @NotNull UUID newApproverId,
            @NotNull UUID expenseId) {
    }
}
