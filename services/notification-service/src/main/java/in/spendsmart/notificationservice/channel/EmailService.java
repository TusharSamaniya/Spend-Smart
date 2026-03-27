package in.spendsmart.notificationservice.channel;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.UUID;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

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

        String htmlBody = """
                <html>
                <body style=\"font-family: Arial, sans-serif; color: #111827;\">
                  <h2>Expense Approval Request</h2>
                  <p>Hello %s,</p>
                  <p>%s submitted an expense that requires your approval.</p>
                  <table cellpadding=\"8\" cellspacing=\"0\" style=\"border-collapse: collapse; border: 1px solid #e5e7eb;\">
                    <tr><td><strong>Employee</strong></td><td>%s</td></tr>
                    <tr><td><strong>Amount</strong></td><td>%s</td></tr>
                    <tr><td><strong>Merchant</strong></td><td>%s</td></tr>
                    <tr><td><strong>Task ID</strong></td><td>%s</td></tr>
                  </table>
                  <p style=\"margin-top: 18px;\">
                    <a href=\"%s\" style=\"background: #16a34a; color: white; padding: 10px 14px; text-decoration: none; border-radius: 4px; margin-right: 8px;\">Approve</a>
                    <a href=\"%s\" style=\"background: #dc2626; color: white; padding: 10px 14px; text-decoration: none; border-radius: 4px;\">Reject</a>
                  </p>
                  <p>If the buttons do not work, use these links:</p>
                  <p>Approve: <a href=\"%s\">%s</a></p>
                  <p>Reject: <a href=\"%s\">%s</a></p>
                </body>
                </html>
                """.formatted(
                escapeHtml(approverName),
                escapeHtml(employeeName),
                escapeHtml(employeeName),
                formatCurrency(amount),
                escapeHtml(merchantName),
                taskId,
                approveUrl,
                rejectUrl,
                approveUrl,
                approveUrl,
                rejectUrl,
                rejectUrl
        );

        sendEmail(toEmail, subject, htmlBody);
    }

    public void sendRejectionNotice(String toEmail, String employeeName, BigDecimal amount, String reason) {
        String subject = "Expense rejected: " + formatCurrency(amount);
        String htmlBody = """
                <html>
                <body style=\"font-family: Arial, sans-serif; color: #111827;\">
                  <h2>Expense Rejected</h2>
                  <p>Hello %s,</p>
                  <p>Your expense claim for <strong>%s</strong> was rejected.</p>
                  <p><strong>Reason:</strong> %s</p>
                </body>
                </html>
                """.formatted(
                escapeHtml(employeeName),
                formatCurrency(amount),
                escapeHtml(reason)
        );

        sendEmail(toEmail, subject, htmlBody);
    }

    public void sendBudgetAlert(String toEmail, String managerName, String budgetName, BigDecimal pctUsed) {
        String subject = "Budget alert: " + escapeHtml(budgetName) + " usage at " + formatPercentage(pctUsed);
        String htmlBody = """
                <html>
                <body style=\"font-family: Arial, sans-serif; color: #111827;\">
                  <h2>Budget Alert</h2>
                  <p>Hello %s,</p>
                  <p>The budget <strong>%s</strong> has reached <strong>%s</strong> utilization.</p>
                  <p>Please review upcoming expenses and adjust allocations if required.</p>
                </body>
                </html>
                """.formatted(
                escapeHtml(managerName),
                escapeHtml(budgetName),
                formatPercentage(pctUsed)
        );

        sendEmail(toEmail, subject, htmlBody);
    }

    private String formatCurrency(BigDecimal amount) {
        NumberFormat numberFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"));
        return numberFormat.format(amount);
    }

    private String formatPercentage(BigDecimal pctUsed) {
        BigDecimal normalized = pctUsed;
        if (pctUsed.compareTo(BigDecimal.ONE) <= 0) {
            normalized = pctUsed.multiply(BigDecimal.valueOf(100));
        }
        return normalized.setScale(2, RoundingMode.HALF_UP) + "%";
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
