package in.spendsmart.gstservice.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class Gstr2bReconciliationService {

    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final BigDecimal ONE_PERCENT = new BigDecimal("0.01");

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public Gstr2bReconciliationService(ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public ReconciliationReport reconcile(UUID orgId, String period, String gstr2bJson) {
        List<PortalInvoice> portalInvoices = parsePortalInvoices(gstr2bJson);
        List<SpendSmartExpense> recordedExpenses = fetchOrgExpenses(orgId, period);

        List<MatchedRecord> matched = new ArrayList<>();
        List<SpendSmartExpense> missingInPortal = new ArrayList<>();
        List<PortalInvoice> missingInSpendSmart = new ArrayList<>(portalInvoices);

        List<SpendSmartExpense> unmatchedExpenses = new ArrayList<>(recordedExpenses);

        for (PortalInvoice portalInvoice : portalInvoices) {
            SpendSmartExpense matchedExpense = findMatchingExpense(portalInvoice, unmatchedExpenses);
            if (matchedExpense != null) {
                matched.add(new MatchedRecord(portalInvoice, matchedExpense));
                unmatchedExpenses.remove(matchedExpense);
                missingInSpendSmart.remove(portalInvoice);
            }
        }

        missingInPortal.addAll(unmatchedExpenses);
        return new ReconciliationReport(matched, missingInPortal, missingInSpendSmart);
    }

    private List<PortalInvoice> parsePortalInvoices(String gstr2bJson) {
        if (gstr2bJson == null || gstr2bJson.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(gstr2bJson);
            JsonNode invoicesNode = resolveInvoicesNode(root);
            if (invoicesNode == null || !invoicesNode.isArray()) {
                return List.of();
            }

            List<PortalInvoice> invoices = new ArrayList<>();
            for (JsonNode node : invoicesNode) {
                String supplierGstin = text(node, "supplierGstin", "ctin", "gstin", "supplier_gstin");
                String invoiceNumber = text(node, "invoiceNumber", "inum", "invoice_number");
                LocalDate invoiceDate = date(node, "invoiceDate", "idt", "invoice_date");
                BigDecimal taxableAmount = decimal(node, "taxableAmount", "txval", "taxable_amount");
                BigDecimal gstAmount = decimal(node, "gstAmount", "tax", "gst_amount");

                invoices.add(new PortalInvoice(
                        normalize(supplierGstin),
                        invoiceNumber,
                        invoiceDate,
                        scale2(taxableAmount),
                        scale2(gstAmount)
                ));
            }
            return invoices;
        } catch (IOException ex) {
            return List.of();
        }
    }

    private JsonNode resolveInvoicesNode(JsonNode root) {
        if (root == null) {
            return null;
        }
        if (root.has("invoices")) {
            return root.get("invoices");
        }
        if (root.has("data") && root.get("data").has("invoices")) {
            return root.get("data").get("invoices");
        }
        if (root.has("b2b")) {
            return root.get("b2b");
        }
        return null;
    }

    private List<SpendSmartExpense> fetchOrgExpenses(UUID orgId, String period) {
        if (orgId == null || period == null || period.isBlank()) {
            return List.of();
        }

        YearMonth yearMonth = YearMonth.parse(period, PERIOD_FORMATTER);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDateExclusive = yearMonth.plusMonths(1).atDay(1);

        return jdbcTemplate.query(
                """
                SELECT
                    id,
                    gstin_supplier,
                    invoice_number,
                    expense_date,
                    COALESCE(taxable_amount, 0) AS taxable_amount,
                    COALESCE(gst_amount, 0) AS gst_amount,
                    COALESCE(total_amount, COALESCE(taxable_amount, 0) + COALESCE(gst_amount, 0)) AS total_amount
                FROM expense_entries
                WHERE org_id = ?
                  AND expense_date >= ?
                  AND expense_date < ?
                  AND gstin_supplier IS NOT NULL
                  AND TRIM(gstin_supplier) <> ''
                """,
                (rs, rowNum) -> mapExpense(rs),
                orgId,
                startDate,
                endDateExclusive
        );
    }

    private SpendSmartExpense findMatchingExpense(PortalInvoice portalInvoice, List<SpendSmartExpense> expenses) {
        BigDecimal portalTotal = portalInvoice.taxableAmount().add(portalInvoice.gstAmount());
        for (SpendSmartExpense expense : expenses) {
            if (!normalize(expense.supplierGstin()).equals(normalize(portalInvoice.supplierGstin()))) {
                continue;
            }

            BigDecimal tolerance = expense.totalAmount().multiply(ONE_PERCENT).setScale(2, RoundingMode.HALF_UP);
            BigDecimal amountDifference = expense.totalAmount().subtract(portalTotal).abs().setScale(2, RoundingMode.HALF_UP);
            if (amountDifference.compareTo(tolerance) <= 0) {
                return expense;
            }
        }
        return null;
    }

    private SpendSmartExpense mapExpense(ResultSet rs) throws SQLException {
        return new SpendSmartExpense(
                UUID.fromString(rs.getString("id")),
                normalize(rs.getString("gstin_supplier")),
                rs.getString("invoice_number"),
                toLocalDate(rs, "expense_date"),
                scale2(rs.getBigDecimal("taxable_amount")),
                scale2(rs.getBigDecimal("gst_amount")),
                scale2(rs.getBigDecimal("total_amount"))
        );
    }

    private LocalDate toLocalDate(ResultSet rs, String column) throws SQLException {
        return rs.getDate(column) == null ? null : rs.getDate(column).toLocalDate();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String text(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode child = node.get(key);
            if (child != null && !child.isNull()) {
                String value = child.asText();
                if (value != null && !value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }

    private BigDecimal decimal(JsonNode node, String... keys) {
        String rawValue = text(node, keys);
        if (rawValue == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(rawValue.trim());
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private LocalDate date(JsonNode node, String... keys) {
        String rawDate = text(node, keys);
        if (rawDate == null) {
            return null;
        }
        try {
            return LocalDate.parse(rawDate);
        } catch (Exception ex) {
            return null;
        }
    }

    private BigDecimal scale2(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public record PortalInvoice(
            String supplierGstin,
            String invoiceNumber,
            LocalDate invoiceDate,
            BigDecimal taxableAmount,
            BigDecimal gstAmount
    ) {
    }

    public record SpendSmartExpense(
            UUID expenseId,
            String supplierGstin,
            String invoiceNumber,
            LocalDate expenseDate,
            BigDecimal taxableAmount,
            BigDecimal gstAmount,
            BigDecimal totalAmount
    ) {
    }

    public record MatchedRecord(PortalInvoice portalInvoice, SpendSmartExpense expense) {
    }

    public record ReconciliationReport(
            List<MatchedRecord> matched,
            List<SpendSmartExpense> missingInPortal,
            List<PortalInvoice> missingInSpendSmart
    ) {
    }
}