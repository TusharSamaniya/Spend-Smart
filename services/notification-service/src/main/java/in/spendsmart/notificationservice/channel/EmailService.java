package in.spendsmart.notificationservice.channel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import java.io.IOException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

@Service
public class EmailService {

    private final JavaMailSender javaMailSender;
    private final String fromEmail;
    private final String dashboardBaseUrl;

    public EmailService(
            JavaMailSender javaMailSender,
            @Value("${notification.from-email}") String fromEmail,
            @Value("${notification.dashboard-base-url:http://localhost:3000}") String dashboardBaseUrl) {
        this.javaMailSender = javaMailSender;
        this.fromEmail = fromEmail;
        this.dashboardBaseUrl = dashboardBaseUrl;
    }

    public void sendEmail(String toEmail, String subject, String htmlBody) {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            javaMailSender.send(mimeMessage);
        } catch (MessagingException ex) {
            throw new IllegalStateException("Failed to send email to " + toEmail, ex);
        }
    }

    public void sendApprovalRequest(
            String toEmail,
            String approverName,
            String employeeName,
            BigDecimal amount,
            String merchantName,
            UUID taskId) {
        String approveUrl = dashboardBaseUrl + "/approvals/" + taskId + "?decision=approve";
        String rejectUrl = dashboardBaseUrl + "/approvals/" + taskId + "?decision=reject";
        String subject = "Expense approval requested: " + formatCurrency(amount) + " at " + merchantName;

        String htmlBody = renderTemplate("templates/approval-request.html", Map.of(
            "approverName", escapeHtml(approverName),
            "employeeName", escapeHtml(employeeName),
            "amount", formatCurrency(amount),
            "merchantName", escapeHtml(merchantName),
            "taskId", taskId.toString(),
            "approveUrl", approveUrl,
            "rejectUrl", rejectUrl
        ));

        sendEmail(toEmail, subject, htmlBody);
    }

    public void sendRejectionNotice(String toEmail, String employeeName, BigDecimal amount, String reason) {
        String subject = "Expense rejected: " + formatCurrency(amount);
        String htmlBody = renderTemplate("templates/rejection-notice.html", Map.of(
            "employeeName", escapeHtml(employeeName),
            "amount", formatCurrency(amount),
            "reason", escapeHtml(reason)
        ));

        sendEmail(toEmail, subject, htmlBody);
    }

    public void sendBudgetAlert(String toEmail, String managerName, String budgetName, BigDecimal pctUsed) {
        BigDecimal normalizedPct = normalizePercentage(pctUsed);
        String pctText = formatPercentage(normalizedPct);
        int used = normalizedPct.setScale(0, RoundingMode.HALF_UP).intValue();
        if (used < 0) {
            used = 0;
        }
        if (used > 100) {
            used = 100;
        }
        int remaining = 100 - used;

        String subject = "Budget alert: " + escapeHtml(budgetName) + " usage at " + pctText;
        String htmlBody = renderTemplate("templates/budget-alert.html", Map.of(
            "managerName", escapeHtml(managerName),
            "budgetName", escapeHtml(budgetName),
            "pctUsed", pctText,
            "progressUsedWidth", used + "%",
            "progressRemainingWidth", remaining + "%",
            "alertLevel", alertLevel(normalizedPct)
        ));

        sendEmail(toEmail, subject, htmlBody);
    }

        public void sendUpiCaptureNotice(String toEmail, BigDecimal amount, String merchantName, UUID expenseId) {
        String confirmUrl = dashboardBaseUrl + "/expenses/" + expenseId + "?action=confirm";
        String dismissUrl = dashboardBaseUrl + "/expenses/" + expenseId + "?action=dismiss";
        String subject = "UPI expense captured: " + formatCurrency(amount) + " at " + merchantName;

        String htmlBody = renderTemplate("templates/upi-capture.html", Map.of(
            "amount", formatCurrency(amount),
            "merchantName", escapeHtml(merchantName),
            "confirmUrl", confirmUrl,
            "dismissUrl", dismissUrl
        ));

        sendEmail(toEmail, subject, htmlBody);
        }

    private String formatCurrency(BigDecimal amount) {
        NumberFormat numberFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"));
        return numberFormat.format(amount);
    }

    private String formatPercentage(BigDecimal pctUsed) {
        BigDecimal normalized = normalizePercentage(pctUsed);
        return normalized.setScale(2, RoundingMode.HALF_UP) + "%";
    }

    private BigDecimal normalizePercentage(BigDecimal pctUsed) {
        BigDecimal normalized = pctUsed;
        if (pctUsed.compareTo(BigDecimal.ONE) <= 0) {
            normalized = pctUsed.multiply(BigDecimal.valueOf(100));
        }
        return normalized;
    }

    private String alertLevel(BigDecimal pct) {
        if (pct.compareTo(BigDecimal.valueOf(100)) >= 0) {
            return "critical";
        }
        if (pct.compareTo(BigDecimal.valueOf(90)) >= 0) {
            return "urgent";
        }
        if (pct.compareTo(BigDecimal.valueOf(75)) >= 0) {
            return "warning";
        }
        return "normal";
    }

    private String renderTemplate(String templatePath, Map<String, String> tokens) {
        String template = loadTemplate(templatePath);
        String rendered = template;
        for (Map.Entry<String, String> token : tokens.entrySet()) {
            rendered = rendered.replace("{" + token.getKey() + "}", token.getValue());
        }
        return rendered;
    }

    private String loadTemplate(String templatePath) {
        try {
            ClassPathResource resource = new ClassPathResource(templatePath);
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load email template: " + templatePath, ex);
        }
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
