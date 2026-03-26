package in.spendsmart.gstservice.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class GstExportService {

    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final String RECONCILIATION_CACHE_KEY_PATTERN = "gstr2b:reconciliation:%s:%s";

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public GstExportService(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public byte[] generateGstReport(UUID orgId, String period) {
        YearMonth yearMonth = YearMonth.parse(period, PERIOD_FORMATTER);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDateExclusive = yearMonth.plusMonths(1).atDay(1);

        List<ExpenseSummaryRow> expenses = fetchExpenseSummary(orgId, startDate, endDateExclusive);
        List<ItcCategorySummaryRow> itcSummary = fetchItcSummary(orgId, startDate, endDateExclusive);
        Gstr2bReconciliationService.ReconciliationReport reconciliationReport = fetchReconciliationReport(orgId, period);

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            CellStyle headerStyle = createHeaderStyle(workbook);

            buildExpenseSummarySheet(workbook, headerStyle, expenses);
            buildItcSummarySheet(workbook, headerStyle, itcSummary);
            buildReconciliationSheet(workbook, headerStyle, reconciliationReport);

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to generate GST report workbook", ex);
        }
    }

    private void buildExpenseSummarySheet(Workbook workbook, CellStyle headerStyle, List<ExpenseSummaryRow> expenses) {
        Sheet sheet = workbook.createSheet("Expense Summary");
        String[] headers = {
                "Date", "Merchant", "Amount", "GST Amount", "CGST", "SGST", "IGST", "HSN Code", "Supplier GSTIN", "ITC Eligible"
        };

        createHeaderRow(sheet, headers, headerStyle);

        int rowIndex = 1;
        for (ExpenseSummaryRow expense : expenses) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(expense.expenseDate() == null ? "" : expense.expenseDate().toString());
            row.createCell(1).setCellValue(expense.merchantName());
            row.createCell(2).setCellValue(toDouble(expense.amount()));
            row.createCell(3).setCellValue(toDouble(expense.gstAmount()));
            row.createCell(4).setCellValue(toDouble(expense.cgstAmount()));
            row.createCell(5).setCellValue(toDouble(expense.sgstAmount()));
            row.createCell(6).setCellValue(toDouble(expense.igstAmount()));
            row.createCell(7).setCellValue(expense.hsnCode());
            row.createCell(8).setCellValue(expense.supplierGstin());
            row.createCell(9).setCellValue(expense.itcEligible() ? "YES" : "NO");
        }

        autoSizeColumns(sheet, headers.length);
    }

    private void buildItcSummarySheet(Workbook workbook, CellStyle headerStyle, List<ItcCategorySummaryRow> itcSummary) {
        Sheet sheet = workbook.createSheet("ITC Summary");
        String[] headers = {"Category", "Total Eligible ITC"};
        createHeaderRow(sheet, headers, headerStyle);

        int rowIndex = 1;
        for (ItcCategorySummaryRow summaryRow : itcSummary) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue(summaryRow.category());
            row.createCell(1).setCellValue(toDouble(summaryRow.totalEligibleItc()));
        }

        autoSizeColumns(sheet, headers.length);
    }

    private void buildReconciliationSheet(
            Workbook workbook,
            CellStyle headerStyle,
            Gstr2bReconciliationService.ReconciliationReport reconciliationReport
    ) {
        Sheet sheet = workbook.createSheet("GSTR-2B Reconciliation");
        String[] headers = {
                "Section", "Supplier GSTIN", "Invoice Number", "Invoice/Expense Date", "Taxable Amount", "GST Amount", "Total Amount", "Reference"
        };
        createHeaderRow(sheet, headers, headerStyle);

        int rowIndex = 1;
        if (reconciliationReport == null) {
            Row row = sheet.createRow(rowIndex);
            row.createCell(0).setCellValue("INFO");
            row.createCell(1).setCellValue("Reconciliation has not been run for this period");
            autoSizeColumns(sheet, headers.length);
            return;
        }

        for (Gstr2bReconciliationService.MatchedRecord record : reconciliationReport.matched()) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue("MATCHED");
            row.createCell(1).setCellValue(record.portalInvoice().supplierGstin());
            row.createCell(2).setCellValue(record.portalInvoice().invoiceNumber());
            row.createCell(3).setCellValue(record.portalInvoice().invoiceDate() == null ? "" : record.portalInvoice().invoiceDate().toString());
            row.createCell(4).setCellValue(toDouble(record.portalInvoice().taxableAmount()));
            row.createCell(5).setCellValue(toDouble(record.portalInvoice().gstAmount()));
            row.createCell(6).setCellValue(toDouble(record.expense().totalAmount()));
            row.createCell(7).setCellValue(record.expense().expenseId().toString());
        }

        for (Gstr2bReconciliationService.SpendSmartExpense expense : reconciliationReport.missingInPortal()) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue("MISSING_IN_PORTAL");
            row.createCell(1).setCellValue(expense.supplierGstin());
            row.createCell(2).setCellValue(expense.invoiceNumber());
            row.createCell(3).setCellValue(expense.expenseDate() == null ? "" : expense.expenseDate().toString());
            row.createCell(4).setCellValue(toDouble(expense.taxableAmount()));
            row.createCell(5).setCellValue(toDouble(expense.gstAmount()));
            row.createCell(6).setCellValue(toDouble(expense.totalAmount()));
            row.createCell(7).setCellValue(expense.expenseId().toString());
        }

        for (Gstr2bReconciliationService.PortalInvoice invoice : reconciliationReport.missingInSpendSmart()) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue("MISSING_IN_SPENDSMART");
            row.createCell(1).setCellValue(invoice.supplierGstin());
            row.createCell(2).setCellValue(invoice.invoiceNumber());
            row.createCell(3).setCellValue(invoice.invoiceDate() == null ? "" : invoice.invoiceDate().toString());
            row.createCell(4).setCellValue(toDouble(invoice.taxableAmount()));
            row.createCell(5).setCellValue(toDouble(invoice.gstAmount()));
            row.createCell(6).setCellValue(toDouble(scale2(invoice.taxableAmount().add(invoice.gstAmount()))));
            row.createCell(7).setCellValue("PORTAL");
        }

        autoSizeColumns(sheet, headers.length);
    }

    private List<ExpenseSummaryRow> fetchExpenseSummary(UUID orgId, LocalDate startDate, LocalDate endDateExclusive) {
        return jdbcTemplate.query(
                """
                SELECT
                    e.expense_date,
                    COALESCE(e.merchant_name, '') AS merchant_name,
                    COALESCE(e.total_amount, 0) AS total_amount,
                    COALESCE(e.gst_amount, 0) AS gst_amount,
                    COALESCE(e.cgst_amount, 0) AS cgst_amount,
                    COALESCE(e.sgst_amount, 0) AS sgst_amount,
                    COALESCE(e.igst_amount, 0) AS igst_amount,
                    COALESCE(e.hsn_code, '') AS hsn_code,
                    COALESCE(e.gstin_supplier, '') AS gstin_supplier,
                    COALESCE(r.is_eligible, FALSE) AS itc_eligible
                FROM expense_entries e
                LEFT JOIN itc_eligibility_rules r
                    ON LOWER(r.expense_category) = LOWER(e.expense_category)
                WHERE e.org_id = ?
                  AND e.expense_date >= ?
                  AND e.expense_date < ?
                ORDER BY e.expense_date ASC, e.id ASC
                """,
                (rs, rowNum) -> mapExpenseSummary(rs),
                orgId,
                startDate,
                endDateExclusive
        );
    }

    private List<ItcCategorySummaryRow> fetchItcSummary(UUID orgId, LocalDate startDate, LocalDate endDateExclusive) {
        return jdbcTemplate.query(
                """
                SELECT
                    e.expense_category,
                    COALESCE(SUM(
                        CASE
                            WHEN r.max_eligible_amount IS NULL THEN COALESCE(e.gst_amount, 0)
                            ELSE LEAST(COALESCE(e.gst_amount, 0), r.max_eligible_amount)
                        END
                    ), 0) AS total_eligible_itc
                FROM expense_entries e
                JOIN itc_eligibility_rules r
                    ON LOWER(r.expense_category) = LOWER(e.expense_category)
                WHERE e.org_id = ?
                  AND e.expense_date >= ?
                  AND e.expense_date < ?
                  AND r.is_eligible = TRUE
                GROUP BY e.expense_category
                ORDER BY e.expense_category ASC
                """,
                (rs, rowNum) -> new ItcCategorySummaryRow(
                        rs.getString("expense_category"),
                        scale2(rs.getBigDecimal("total_eligible_itc"))
                ),
                orgId,
                startDate,
                endDateExclusive
        );
    }

    private Gstr2bReconciliationService.ReconciliationReport fetchReconciliationReport(UUID orgId, String period) {
        String cacheKey = RECONCILIATION_CACHE_KEY_PATTERN.formatted(orgId, period);
        String cachedJson = redisTemplate.opsForValue().get(cacheKey);
        if (cachedJson == null || cachedJson.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readValue(cachedJson, Gstr2bReconciliationService.ReconciliationReport.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private ExpenseSummaryRow mapExpenseSummary(ResultSet rs) throws SQLException {
        return new ExpenseSummaryRow(
                rs.getDate("expense_date") == null ? null : rs.getDate("expense_date").toLocalDate(),
                rs.getString("merchant_name"),
                scale2(rs.getBigDecimal("total_amount")),
                scale2(rs.getBigDecimal("gst_amount")),
                scale2(rs.getBigDecimal("cgst_amount")),
                scale2(rs.getBigDecimal("sgst_amount")),
                scale2(rs.getBigDecimal("igst_amount")),
                rs.getString("hsn_code"),
                rs.getString("gstin_supplier"),
                rs.getBoolean("itc_eligible")
        );
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        return style;
    }

    private void createHeaderRow(Sheet sheet, String[] headers, CellStyle headerStyle) {
        Row headerRow = sheet.createRow(0);
        for (int columnIndex = 0; columnIndex < headers.length; columnIndex++) {
            Cell cell = headerRow.createCell(columnIndex);
            cell.setCellValue(headers[columnIndex]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void autoSizeColumns(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private BigDecimal scale2(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private double toDouble(BigDecimal value) {
        return scale2(value).doubleValue();
    }

    private record ExpenseSummaryRow(
            LocalDate expenseDate,
            String merchantName,
            BigDecimal amount,
            BigDecimal gstAmount,
            BigDecimal cgstAmount,
            BigDecimal sgstAmount,
            BigDecimal igstAmount,
            String hsnCode,
            String supplierGstin,
            boolean itcEligible
    ) {
    }

    private record ItcCategorySummaryRow(String category, BigDecimal totalEligibleItc) {
    }
}