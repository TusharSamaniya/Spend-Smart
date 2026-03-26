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
public class ItcTrackerService {

    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final JdbcTemplate jdbcTemplate;

    public ItcTrackerService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ItcEligibilityResult checkItcEligibility(UUID orgId, String expenseCategory, BigDecimal amount) {
        if (orgId == null || expenseCategory == null || expenseCategory.isBlank() || amount == null) {
            return new ItcEligibilityResult(false, ZERO_MONEY, "Invalid ITC eligibility input", ZERO_MONEY);
        }

        String normalizedCategory = expenseCategory.trim();
        BigDecimal normalizedAmount = amount.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        List<ItcRuleRow> rules = jdbcTemplate.query(
                """
                SELECT is_eligible, max_eligible_amount, blocked_reason
                FROM itc_eligibility_rules
                WHERE LOWER(expense_category) = LOWER(?)
                LIMIT 1
                """,
                (rs, rowNum) -> mapRule(rs),
                normalizedCategory
        );

        if (rules.isEmpty()) {
            return new ItcEligibilityResult(false, ZERO_MONEY, "No ITC rule found for expense category", ZERO_MONEY);
        }

        ItcRuleRow rule = rules.get(0);
        if (!rule.eligible()) {
            String blockedReason = rule.blockedReason() == null || rule.blockedReason().isBlank()
                    ? "Category blocked for ITC"
                    : rule.blockedReason();
            return new ItcEligibilityResult(false, ZERO_MONEY, blockedReason, ZERO_MONEY);
        }

        BigDecimal eligibleAmount = rule.maxEligibleAmount() == null
                ? normalizedAmount
                : normalizedAmount.min(rule.maxEligibleAmount()).setScale(2, RoundingMode.HALF_UP);

        BigDecimal gstRate = fetchGstRateForCategory(normalizedCategory);
        BigDecimal itcAmount = eligibleAmount.multiply(gstRate).setScale(2, RoundingMode.HALF_UP);

        return new ItcEligibilityResult(true, eligibleAmount, null, itcAmount);
    }

    public ItcSummaryResult getItcSummary(UUID orgId, String period) {
        if (orgId == null || period == null || period.isBlank()) {
            return new ItcSummaryResult(period, ZERO_MONEY, 0);
        }

        YearMonth yearMonth = YearMonth.parse(period, PERIOD_FORMATTER);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDateExclusive = yearMonth.plusMonths(1).atDay(1);

        List<ItcSummaryRow> rows = jdbcTemplate.query(
                """
                SELECT
                    COALESCE(SUM(
                        CASE
                            WHEN r.max_eligible_amount IS NULL THEN e.gst_amount
                            ELSE LEAST(e.gst_amount, r.max_eligible_amount)
                        END
                    ), 0) AS total_itc_amount,
                    COUNT(*) AS eligible_expense_count
                FROM expense_entries e
                JOIN itc_eligibility_rules r
                    ON LOWER(r.expense_category) = LOWER(e.expense_category)
                WHERE e.org_id = ?
                  AND e.expense_date >= ?
                  AND e.expense_date < ?
                  AND r.is_eligible = TRUE
                """,
                (rs, rowNum) -> new ItcSummaryRow(
                        rs.getBigDecimal("total_itc_amount"),
                        rs.getLong("eligible_expense_count")
                ),
                orgId,
                startDate,
                endDateExclusive
        );

        if (rows.isEmpty()) {
            return new ItcSummaryResult(period, ZERO_MONEY, 0);
        }

        ItcSummaryRow summary = rows.get(0);
        BigDecimal totalItcAmount = summary.totalItcAmount() == null
                ? ZERO_MONEY
                : summary.totalItcAmount().setScale(2, RoundingMode.HALF_UP);

        return new ItcSummaryResult(period, totalItcAmount, summary.eligibleExpenseCount());
    }

    private BigDecimal fetchGstRateForCategory(String expenseCategory) {
        List<BigDecimal> rates = jdbcTemplate.query(
                """
                SELECT gst_rate
                FROM gst_rate_master
                WHERE LOWER(category) = LOWER(?)
                  AND effective_from <= CURRENT_DATE
                ORDER BY effective_from DESC
                LIMIT 1
                """,
                (rs, rowNum) -> rs.getBigDecimal("gst_rate"),
                expenseCategory
        );

        if (rates.isEmpty() || rates.get(0) == null) {
            return BigDecimal.ZERO;
        }

        return rates.get(0).setScale(4, RoundingMode.HALF_UP);
    }

    private ItcRuleRow mapRule(ResultSet rs) throws SQLException {
        return new ItcRuleRow(
                rs.getBoolean("is_eligible"),
                rs.getBigDecimal("max_eligible_amount"),
                rs.getString("blocked_reason")
        );
    }

    private record ItcRuleRow(boolean eligible, BigDecimal maxEligibleAmount, String blockedReason) {
    }

    private record ItcSummaryRow(BigDecimal totalItcAmount, long eligibleExpenseCount) {
    }

    public record ItcEligibilityResult(
            boolean eligible,
            BigDecimal eligibleAmount,
            String blockedReason,
            BigDecimal itcAmount
    ) {
    }

    public record ItcSummaryResult(
            String period,
            BigDecimal totalItcAmount,
            long eligibleExpenseCount
    ) {
    }
}