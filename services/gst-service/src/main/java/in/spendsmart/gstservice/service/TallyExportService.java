package in.spendsmart.gstservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TallyExportService {

    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter TALLY_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final JdbcTemplate jdbcTemplate;

    public TallyExportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String generateTallyXml(UUID orgId, String period) {
        YearMonth yearMonth = YearMonth.parse(period, PERIOD_FORMATTER);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDateExclusive = yearMonth.plusMonths(1).atDay(1);

        List<ExpenseVoucherRow> expenses = fetchExpenses(orgId, startDate, endDateExclusive);

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<TALLYMESSAGE>");

        for (ExpenseVoucherRow expense : expenses) {
            appendVoucher(xml, expense);
        }

        xml.append("</TALLYMESSAGE>");
        return xml.toString();
    }

    private void appendVoucher(StringBuilder xml, ExpenseVoucherRow expense) {
        BigDecimal totalAmount = scale2(expense.totalAmount());
        BigDecimal taxableAmount = scale2(totalAmount
                .subtract(scale2(expense.cgstAmount()))
                .subtract(scale2(expense.sgstAmount()))
                .subtract(scale2(expense.igstAmount())));

        xml.append("<VOUCHER>");
        xml.append("<VOUCHERTYPENAME>Purchase</VOUCHERTYPENAME>");
        xml.append("<VOUCHERDATE>").append(formatDate(expense.expenseDate())).append("</VOUCHERDATE>");
        xml.append("<PARTYLEDGERNAME>").append(escapeXml(expense.merchantName())).append("</PARTYLEDGERNAME>");
        xml.append("<AMOUNT>").append(negative(totalAmount)).append("</AMOUNT>");

        appendLedger(xml, "Purchase Ledger", taxableAmount);

        if (scale2(expense.cgstAmount()).compareTo(BigDecimal.ZERO) > 0) {
            appendLedger(xml, "CGST Ledger", scale2(expense.cgstAmount()));
        }
        if (scale2(expense.sgstAmount()).compareTo(BigDecimal.ZERO) > 0) {
            appendLedger(xml, "SGST Ledger", scale2(expense.sgstAmount()));
        }
        if (scale2(expense.igstAmount()).compareTo(BigDecimal.ZERO) > 0) {
            appendLedger(xml, "IGST Ledger", scale2(expense.igstAmount()));
        }

        appendLedger(xml, expense.merchantName(), negative(totalAmount));
        xml.append("</VOUCHER>");
    }

    private void appendLedger(StringBuilder xml, String ledgerName, BigDecimal amount) {
        xml.append("<ALLLEDGERENTRIES>");
        xml.append("<LEDGERNAME>").append(escapeXml(ledgerName)).append("</LEDGERNAME>");
        xml.append("<AMOUNT>").append(scale2(amount)).append("</AMOUNT>");
        xml.append("</ALLLEDGERENTRIES>");
    }

    private List<ExpenseVoucherRow> fetchExpenses(UUID orgId, LocalDate startDate, LocalDate endDateExclusive) {
        return jdbcTemplate.query(
                """
                SELECT
                    id,
                    expense_date,
                    COALESCE(merchant_name, 'Unknown Merchant') AS merchant_name,
                    COALESCE(total_amount, 0) AS total_amount,
                    COALESCE(cgst_amount, 0) AS cgst_amount,
                    COALESCE(sgst_amount, 0) AS sgst_amount,
                    COALESCE(igst_amount, 0) AS igst_amount
                FROM expense_entries
                WHERE org_id = ?
                  AND expense_date >= ?
                  AND expense_date < ?
                ORDER BY expense_date ASC, id ASC
                """,
                (rs, rowNum) -> mapExpense(rs),
                orgId,
                startDate,
                endDateExclusive
        );
    }

    private ExpenseVoucherRow mapExpense(ResultSet rs) throws SQLException {
        String id = rs.getString("id");
        if (id == null) {
            throw new SQLException("Expense id cannot be null");
        }
        return new ExpenseVoucherRow(
                UUID.fromString(id),
                rs.getDate("expense_date") == null ? null : rs.getDate("expense_date").toLocalDate(),
                rs.getString("merchant_name"),
                rs.getBigDecimal("total_amount"),
                rs.getBigDecimal("cgst_amount"),
                rs.getBigDecimal("sgst_amount"),
                rs.getBigDecimal("igst_amount")
        );
    }

    private String formatDate(LocalDate date) {
        return date == null ? "" : date.format(TALLY_DATE_FORMATTER);
    }

    private BigDecimal negative(BigDecimal amount) {
        return scale2(amount).negate().setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scale2(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private record ExpenseVoucherRow(
            UUID expenseId,
            LocalDate expenseDate,
            String merchantName,
            BigDecimal totalAmount,
            BigDecimal cgstAmount,
            BigDecimal sgstAmount,
            BigDecimal igstAmount
    ) {
    }
}