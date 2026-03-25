package in.spendsmart.ocrservice.parser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class IndianReceiptParser {

    private static final Pattern CURRENCY_AMOUNT_PATTERN = Pattern.compile(
            "(?:Rs\\.?|INR|₹)\\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DATE_PATTERN = Pattern.compile("\\b(\\d{2}[/-]\\d{2}[/-]\\d{4})\\b");

    private static final Pattern GST_AMOUNT_PATTERN = Pattern.compile(
            "(?:GST|Tax)\\s*[:=-]?\\s*(?:Rs\\.?|INR|₹)?\\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CGST_AMOUNT_PATTERN = Pattern.compile(
            "CGST\\s*[:=-]?\\s*(?:Rs\\.?|INR|₹)?\\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern SGST_AMOUNT_PATTERN = Pattern.compile(
            "SGST\\s*[:=-]?\\s*(?:Rs\\.?|INR|₹)?\\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern HSN_CODE_PATTERN = Pattern.compile("HSN\\s*[:=-]?\\s*(\\d{4,8})", Pattern.CASE_INSENSITIVE);

    private static final DateTimeFormatter SLASH_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DASH_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH);

    public OcrResult extract(String rawOcrText) {
        if (rawOcrText == null || rawOcrText.isBlank()) {
            return new OcrResult(null, null, null, null, null, null, null);
        }

        BigDecimal amount = extractAmount(rawOcrText).orElse(null);
        String merchantName = extractMerchantName(rawOcrText).orElse(null);
        LocalDate receiptDate = extractDate(rawOcrText).orElse(null);
        BigDecimal gstAmount = extractAmountByPattern(rawOcrText, GST_AMOUNT_PATTERN).orElse(null);
        BigDecimal cgst = extractAmountByPattern(rawOcrText, CGST_AMOUNT_PATTERN).orElse(null);
        BigDecimal sgst = extractAmountByPattern(rawOcrText, SGST_AMOUNT_PATTERN).orElse(null);
        String hsnCode = extractHsnCode(rawOcrText).orElse(null);

        return new OcrResult(amount, merchantName, receiptDate, gstAmount, cgst, sgst, hsnCode);
    }

    private Optional<BigDecimal> extractAmount(String text) {
        Matcher matcher = CURRENCY_AMOUNT_PATTERN.matcher(text);
        List<BigDecimal> matches = new ArrayList<>();
        while (matcher.find()) {
            parseAmount(matcher.group(1)).ifPresent(matches::add);
        }

        if (matches.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(matches.get(matches.size() - 1));
    }

    private Optional<BigDecimal> extractAmountByPattern(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return parseAmount(matcher.group(1));
    }

    private Optional<LocalDate> extractDate(String text) {
        Matcher matcher = DATE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }

        String dateValue = matcher.group(1);
        try {
            if (dateValue.contains("/")) {
                return Optional.of(LocalDate.parse(dateValue, SLASH_DATE_FORMATTER));
            }
            return Optional.of(LocalDate.parse(dateValue, DASH_DATE_FORMATTER));
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    private Optional<String> extractMerchantName(String text) {
        String[] lines = text.split("\\R+");
        for (String line : lines) {
            String candidate = line == null ? "" : line.trim();
            if (candidate.isEmpty() || candidate.length() < 3 || containsMostlyDigits(candidate)) {
                continue;
            }

            String lower = candidate.toLowerCase(Locale.ENGLISH);
            if (lower.contains("invoice") || lower.contains("bill") || lower.contains("receipt")
                    || lower.contains("gst") || lower.contains("tax") || lower.contains("date")
                    || lower.contains("total") || lower.contains("phone") || lower.contains("mobile")) {
                continue;
            }

            return Optional.of(candidate);
        }
        return Optional.empty();
    }

    private Optional<String> extractHsnCode(String text) {
        Matcher matcher = HSN_CODE_PATTERN.matcher(text);
        if (matcher.find()) {
            return Optional.ofNullable(matcher.group(1));
        }
        return Optional.empty();
    }

    private Optional<BigDecimal> parseAmount(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.replace(",", "").trim();
        try {
            return Optional.of(new BigDecimal(normalized));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private boolean containsMostlyDigits(String value) {
        long digits = value.chars().filter(Character::isDigit).count();
        return digits > value.length() / 2;
    }

    public record OcrResult(
            BigDecimal amount,
            String merchantName,
            LocalDate receiptDate,
            BigDecimal gstAmount,
            BigDecimal cgst,
            BigDecimal sgst,
            String hsnCode
    ) {
    }
}
