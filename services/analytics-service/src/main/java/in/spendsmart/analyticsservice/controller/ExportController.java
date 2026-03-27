package in.spendsmart.analyticsservice.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/export")
public class ExportController {

    private final JdbcTemplate jdbcTemplate;

    public ExportController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/csv")
    public ResponseEntity<String> exportCsv(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam UUID orgId
    ) {
        List<ExportRow> rows = fetchExportRows(orgId, from, to);

        StringBuilder csv = new StringBuilder();
        csv.append("id,orgId,userId,categoryId,expenseDate,merchantName,paymentMethod,status,amount,amountBase,currency,baseCurrency,notes,tags,createdAt\n");

        for (ExportRow row : rows) {
            csv.append(toCsv(row.id())).append(',')
                    .append(toCsv(row.orgId())).append(',')
                    .append(toCsv(row.userId())).append(',')
                    .append(toCsv(row.categoryId())).append(',')
                    .append(toCsv(row.expenseDate())).append(',')
                    .append(toCsv(row.merchantName())).append(',')
                    .append(toCsv(row.paymentMethod())).append(',')
                    .append(toCsv(row.status())).append(',')
                    .append(toCsv(row.amount())).append(',')
                    .append(toCsv(row.amountBase())).append(',')
                    .append(toCsv(row.currency())).append(',')
                    .append(toCsv(row.baseCurrency())).append(',')
                    .append(toCsv(row.notes())).append(',')
                    .append(toCsv(String.join("|", row.tags()))).append(',')
                    .append(toCsv(row.createdAt()))
                    .append('\n');
        }

        String filename = String.format("expenses_%s_%s.csv", from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/csv"))
                .body(csv.toString());
    }

    @GetMapping("/xlsx")
    public ResponseEntity<byte[]> exportXlsx(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam UUID orgId
    ) {
        List<ExportRow> rows = fetchExportRows(orgId, from, to);

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Expenses");
            String[] headers = {
                    "ID", "Org ID", "User ID", "Category ID", "Expense Date", "Merchant", "Payment Method",
                    "Status", "Amount", "Amount Base", "Currency", "Base Currency", "Notes", "Tags", "Created At"
            };

            CellStyle headerStyle = createHeaderStyle(workbook);
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (ExportRow row : rows) {
                Row dataRow = sheet.createRow(rowIndex++);
                dataRow.createCell(0).setCellValue(nullToEmpty(row.id()));
                dataRow.createCell(1).setCellValue(nullToEmpty(row.orgId()));
                dataRow.createCell(2).setCellValue(nullToEmpty(row.userId()));
                dataRow.createCell(3).setCellValue(nullToEmpty(row.categoryId()));
                dataRow.createCell(4).setCellValue(nullToEmpty(row.expenseDate()));
                dataRow.createCell(5).setCellValue(nullToEmpty(row.merchantName()));
                dataRow.createCell(6).setCellValue(nullToEmpty(row.paymentMethod()));
                dataRow.createCell(7).setCellValue(nullToEmpty(row.status()));
                dataRow.createCell(8).setCellValue(row.amount().doubleValue());
                dataRow.createCell(9).setCellValue(row.amountBase().doubleValue());
                dataRow.createCell(10).setCellValue(nullToEmpty(row.currency()));
                dataRow.createCell(11).setCellValue(nullToEmpty(row.baseCurrency()));
                dataRow.createCell(12).setCellValue(nullToEmpty(row.notes()));
                dataRow.createCell(13).setCellValue(String.join("|", row.tags()));
                dataRow.createCell(14).setCellValue(nullToEmpty(row.createdAt()));
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);
            String filename = String.format("expenses_%s_%s.xlsx", from, to);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(outputStream.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to generate XLSX export", exception);
        }
    }

    @GetMapping("/pdf")
    public ResponseEntity<String> exportPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam UUID orgId
    ) {
        List<ExportRow> rows = fetchExportRows(orgId, from, to);

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>Expense Export</title>")
                .append("<style>body{font-family:Arial,sans-serif;padding:20px;}table{border-collapse:collapse;width:100%;}")
                .append("th,td{border:1px solid #ddd;padding:8px;font-size:12px;}th{background:#f5f5f5;text-align:left;}")
                .append("h1{font-size:20px;} .muted{color:#666;}</style></head><body>")
                .append("<h1>Expense Export</h1>")
                .append("<p class=\"muted\">Org: ").append(orgId).append(" | Period: ").append(from).append(" to ").append(to).append("</p>")
                .append("<table><thead><tr>")
                .append("<th>Date</th><th>Merchant</th><th>Category</th><th>Status</th><th>Amount Base</th><th>Currency</th><th>Notes</th>")
                .append("</tr></thead><tbody>");

        for (ExportRow row : rows) {
            html.append("<tr>")
                    .append("<td>").append(escapeHtml(row.expenseDate())).append("</td>")
                    .append("<td>").append(escapeHtml(row.merchantName())).append("</td>")
                    .append("<td>").append(escapeHtml(row.categoryId())).append("</td>")
                    .append("<td>").append(escapeHtml(row.status())).append("</td>")
                    .append("<td>").append(row.amountBase()).append("</td>")
                    .append("<td>").append(escapeHtml(row.baseCurrency())).append("</td>")
                    .append("<td>").append(escapeHtml(row.notes())).append("</td>")
                    .append("</tr>");
        }

        html.append("</tbody></table></body></html>");

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html.toString());
    }

    private List<ExportRow> fetchExportRows(UUID orgId, LocalDate from, LocalDate to) {
        return jdbcTemplate.query(
                """
                SELECT
                    e.id,
                    e.org_id,
                    e.user_id,
                    e.category_id,
                    e.expense_date,
                    COALESCE(e.merchant_name, '') AS merchant_name,
                    COALESCE(e.payment_method, '') AS payment_method,
                    COALESCE(e.status, '') AS status,
                    COALESCE(e.amount, 0) AS amount,
                    COALESCE(e.amount_base, 0) AS amount_base,
                    COALESCE(e.currency, '') AS currency,
                    COALESCE(e.base_currency, '') AS base_currency,
                    COALESCE(e.notes, '') AS notes,
                    COALESCE(e.tags, ARRAY[]::text[]) AS tags,
                    e.created_at
                FROM expenses e
                WHERE e.org_id = ?
                  AND e.expense_date BETWEEN ? AND ?
                  AND e.deleted_at IS NULL
                ORDER BY e.expense_date ASC, e.created_at ASC
                """,
                (rs, rowNum) -> mapRow(rs),
                orgId,
                from,
                to
        );
    }

    private ExportRow mapRow(ResultSet rs) throws SQLException {
        Array tagArray = rs.getArray("tags");
        List<String> tags = new ArrayList<>();
        if (tagArray != null && tagArray.getArray() instanceof String[] tagValues) {
            for (String value : tagValues) {
                tags.add(value == null ? "" : value);
            }
        }

        return new ExportRow(
                uuidToString(rs.getObject("id")),
                uuidToString(rs.getObject("org_id")),
                uuidToString(rs.getObject("user_id")),
                uuidToString(rs.getObject("category_id")),
                rs.getDate("expense_date") == null ? "" : rs.getDate("expense_date").toLocalDate().toString(),
                rs.getString("merchant_name"),
                rs.getString("payment_method"),
                rs.getString("status"),
                rs.getBigDecimal("amount") == null ? BigDecimal.ZERO : rs.getBigDecimal("amount"),
                rs.getBigDecimal("amount_base") == null ? BigDecimal.ZERO : rs.getBigDecimal("amount_base"),
                rs.getString("currency"),
                rs.getString("base_currency"),
                rs.getString("notes"),
                tags,
                rs.getObject("created_at", OffsetDateTime.class) == null ? "" : rs.getObject("created_at", OffsetDateTime.class).toString()
        );
    }

    private String uuidToString(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof UUID uuid) {
            return uuid.toString();
        }
        return value.toString();
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        return style;
    }

    private String toCsv(Object value) {
        String text = value == null ? "" : value.toString();
        String escaped = text.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
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

    private record ExportRow(
            String id,
            String orgId,
            String userId,
            String categoryId,
            String expenseDate,
            String merchantName,
            String paymentMethod,
            String status,
            BigDecimal amount,
            BigDecimal amountBase,
            String currency,
            String baseCurrency,
            String notes,
            List<String> tags,
            String createdAt
    ) {
    }
}
