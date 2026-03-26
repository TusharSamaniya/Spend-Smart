package in.spendsmart.upiservice.parser;

import in.spendsmart.upiservice.worker.UpiTransactionMessage;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class SmsParser {

    private static final List<Pattern> AMOUNT_PATTERNS = List.of(
            Pattern.compile("(?i)(?:rs\\.?|inr)\\s*([0-9][0-9,]*\\.?[0-9]{0,2})"),
            Pattern.compile("(?i)debited\\s*(?:for|with|by)?\\s*(?:rs\\.?|inr)?\\s*([0-9][0-9,]*\\.?[0-9]{0,2})")
    );

    private static final List<Pattern> VPA_PATTERNS = List.of(
            Pattern.compile("(?i)upi[:\\s-]*([a-z0-9._-]{2,}@[a-z0-9.-]{2,})"),
            Pattern.compile("(?i)([a-z0-9._-]{2,}@[a-z0-9.-]{2,})")
    );

    private static final List<Pattern> DATE_PATTERNS = List.of(
            Pattern.compile("(?i)(?:on|dated)\\s*(\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4})"),
            Pattern.compile("(?i)(?:on|dated)\\s*(\\d{1,2}-[a-z]{3}-\\d{2,4})"),
            Pattern.compile("(?i)(\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4})")
    );

    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("d-M-uuuu"),
            DateTimeFormatter.ofPattern("d-M-uu"),
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("d/M/uu"),
            DateTimeFormatter.ofPattern("d-MMM-uuuu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d-MMM-uu", Locale.ENGLISH)
    );

    public UpiTransactionMessage parseSms(String smsText) {
        if (!StringUtils.hasText(smsText)) {
            throw new IllegalArgumentException("smsText cannot be blank");
        }

        BigDecimal amount = extractAmount(smsText);
        String vpa = extractVpa(smsText);
        OffsetDateTime txnTimestamp = extractDate(smsText);

        if (amount == null || !StringUtils.hasText(vpa) || txnTimestamp == null) {
            throw new IllegalArgumentException("Unable to extract required UPI fields from SMS");
        }

        return new UpiTransactionMessage(
                generateDeterministicRefId(smsText),
                null,
                vpa,
                amount,
                txnTimestamp,
                null
        );
    }

    private BigDecimal extractAmount(String smsText) {
        for (Pattern pattern : AMOUNT_PATTERNS) {
            Matcher matcher = pattern.matcher(smsText);
            if (matcher.find()) {
                String amountText = matcher.group(1).replace(",", "").trim();
                try {
                    return new BigDecimal(amountText);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return null;
    }

    private String extractVpa(String smsText) {
        for (Pattern pattern : VPA_PATTERNS) {
            Matcher matcher = pattern.matcher(smsText);
            if (matcher.find()) {
                return matcher.group(1).trim().toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }

    private OffsetDateTime extractDate(String smsText) {
        for (Pattern pattern : DATE_PATTERNS) {
            Matcher matcher = pattern.matcher(smsText);
            if (matcher.find()) {
                String rawDate = matcher.group(1).trim();
                LocalDate parsedDate = parseLocalDate(rawDate);
                if (parsedDate != null) {
                    return LocalDateTime.of(parsedDate, java.time.LocalTime.MIDNIGHT).atOffset(ZoneOffset.UTC);
                }
            }
        }
        return null;
    }

    private LocalDate parseLocalDate(String rawDate) {
        String normalized = rawDate.replace('/', '-');
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(normalized, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private String generateDeterministicRefId(String smsText) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(smsText.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return "sms-" + hex.substring(0, 32);
        } catch (Exception ignored) {
            return "sms-" + UUID.randomUUID();
        }
    }
}
