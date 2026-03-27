package in.spendsmart.notificationservice.channel;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class WhatsAppService {

    private final RestClient restClient;
    private final String token;
    private final String phoneId;

    public WhatsAppService(
            RestClient.Builder restClientBuilder,
            @Value("${whatsapp.token}") String token,
            @Value("${whatsapp.phone-id}") String phoneId) {
        this.restClient = restClientBuilder.baseUrl("https://graph.facebook.com").build();
        this.token = token;
        this.phoneId = phoneId;
    }

    public void sendTemplateMessage(String toPhone, String templateName, List<?> params) {
        List<TemplateParameter> templateParams = params.stream()
                .map(param -> new TemplateParameter("text", String.valueOf(param)))
                .toList();

        WhatsAppMessageRequest requestBody = new WhatsAppMessageRequest(
                "whatsapp",
                toPhone,
                "template",
                new Template(
                        templateName,
                        new Language("en"),
                        List.of(new TemplateComponent("body", templateParams))
                )
        );

        restClient.post()
                .uri("/{phoneId}/messages", phoneId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .toBodilessEntity();
    }

    public void sendApprovalRequest(
            String phone,
            String approverName,
            String employeeName,
            BigDecimal amount,
            String merchantName) {
        sendTemplateMessage(
                phone,
                "expense_approval_request",
                List.of(
                        approverName,
                        employeeName,
                        formatCurrency(amount),
                        merchantName
                )
        );
    }

    public void sendRejectionNotice(String phone, String employeeName, String reason) {
        sendTemplateMessage(
                phone,
                "expense_rejection_notice",
                List.of(employeeName, reason)
        );
    }

    public void sendDailySpendSummary(String phone, BigDecimal totalToday, String topCategory) {
        sendTemplateMessage(
                phone,
                "daily_spend_summary",
                List.of(formatCurrency(totalToday), topCategory)
        );
    }

    private String formatCurrency(BigDecimal amount) {
        NumberFormat numberFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"));
        return numberFormat.format(amount);
    }

    // WhatsApp template messages must be pre-approved by Meta.
    // During development, use test phone numbers from Meta developer portal.
    private record WhatsAppMessageRequest(
            String messaging_product,
            String to,
            String type,
            Template template) {
    }

    private record Template(
            String name,
            Language language,
            List<TemplateComponent> components) {
    }

    private record Language(String code) {
    }

    private record TemplateComponent(String type, List<TemplateParameter> parameters) {
    }

    private record TemplateParameter(String type, String text) {
    }
}
