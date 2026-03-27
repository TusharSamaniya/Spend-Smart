package in.spendsmart.analyticsservice.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TrendAnalysisService {

    private static final DateTimeFormatter YEAR_MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final EntityManager entityManager;

    public TrendReport getMonthlyTrends(UUID orgId, int months) {
        if (orgId == null) {
            throw new IllegalArgumentException("orgId is required");
        }
        if (months <= 0) {
            throw new IllegalArgumentException("months must be greater than 0");
        }

        YearMonth currentMonth = YearMonth.now();
        YearMonth startMonth = currentMonth.minusMonths(months - 1L);
        LocalDate from = startMonth.atDay(1);
        LocalDate to = currentMonth.atEndOfMonth();

        List<YearMonth> monthSequence = buildMonthSequence(startMonth, currentMonth);
        List<TrendReport.MonthlyTotal> monthlyTotals = getMonthlyTotals(orgId, from, to, monthSequence);
        List<TrendReport.CategoryTrend> categoryTrends = getCategoryTrends(orgId, from, to, monthSequence);

        return TrendReport.builder()
                .monthlyTotals(monthlyTotals)
                .categoryTrends(categoryTrends)
                .yoyChange(getYearOnYearComparison(orgId).getYoyChange())
                .build();
    }

    public TrendReport getYearOnYearComparison(UUID orgId) {
        if (orgId == null) {
            throw new IllegalArgumentException("orgId is required");
        }

        YearMonth currentMonth = YearMonth.now();
        LocalDate currentStart = currentMonth.atDay(1);
        LocalDate currentEnd = currentMonth.atEndOfMonth();

        LocalDate lastYearStart = currentStart.minusYears(1);
        LocalDate lastYearEnd = currentEnd.minusYears(1);

        BigDecimal currentTotal = getTotalForRange(orgId, currentStart, currentEnd);
        BigDecimal lastYearTotal = getTotalForRange(orgId, lastYearStart, lastYearEnd);

        BigDecimal yoyChange = calculatePctChange(currentTotal, lastYearTotal);

        return TrendReport.builder()
                .monthlyTotals(List.of())
                .categoryTrends(List.of())
                .yoyChange(yoyChange)
                .build();
    }

    @SuppressWarnings("unchecked")
    private List<TrendReport.MonthlyTotal> getMonthlyTotals(
            UUID orgId,
            LocalDate from,
            LocalDate to,
            List<YearMonth> monthSequence
    ) {
        Query query = entityManager.createNativeQuery("""
                SELECT date_trunc('month', expense_date)::date AS month_start,
                       COALESCE(SUM(total), 0) AS total
                FROM mv_daily_spend
                WHERE org_id = :orgId
                  AND expense_date BETWEEN :from AND :to
                GROUP BY month_start
                ORDER BY month_start
                """);
        query.setParameter("orgId", orgId);
        query.setParameter("from", from);
        query.setParameter("to", to);

        List<Object[]> rows = query.getResultList();
        Map<YearMonth, BigDecimal> totalsByMonth = new LinkedHashMap<>();
        for (YearMonth month : monthSequence) {
            totalsByMonth.put(month, BigDecimal.ZERO);
        }
        for (Object[] row : rows) {
            LocalDate monthStart = ((java.sql.Date) row[0]).toLocalDate();
            totalsByMonth.put(YearMonth.from(monthStart), asBigDecimal(row[1]));
        }

        List<TrendReport.MonthlyTotal> monthlyTotals = new ArrayList<>();
        BigDecimal previous = null;
        for (YearMonth month : monthSequence) {
            BigDecimal total = totalsByMonth.getOrDefault(month, BigDecimal.ZERO);
            BigDecimal pctChange = previous == null ? BigDecimal.ZERO : calculatePctChange(total, previous);
            monthlyTotals.add(TrendReport.MonthlyTotal.builder()
                    .yearMonth(month.format(YEAR_MONTH_FORMATTER))
                    .total(total)
                    .pctChangeFromPreviousMonth(pctChange)
                    .build());
            previous = total;
        }

        return monthlyTotals;
    }

    @SuppressWarnings("unchecked")
    private List<TrendReport.CategoryTrend> getCategoryTrends(
            UUID orgId,
            LocalDate from,
            LocalDate to,
            List<YearMonth> monthSequence
    ) {
        Query query = entityManager.createNativeQuery("""
                WITH top_categories AS (
                    SELECT category_id,
                           COALESCE(SUM(total), 0) AS total
                    FROM mv_daily_spend
                    WHERE org_id = :orgId
                      AND expense_date BETWEEN :from AND :to
                    GROUP BY category_id
                    ORDER BY total DESC
                    LIMIT 5
                )
                SELECT tc.category_id,
                       COALESCE(c.name, 'Uncategorized') AS category_name,
                       date_trunc('month', ds.expense_date)::date AS month_start,
                       COALESCE(SUM(ds.total), 0) AS total
                FROM top_categories tc
                LEFT JOIN mv_daily_spend ds
                       ON ds.org_id = :orgId
                      AND ds.expense_date BETWEEN :from AND :to
                      AND ds.category_id IS NOT DISTINCT FROM tc.category_id
                LEFT JOIN categories c ON c.id = tc.category_id
                GROUP BY tc.category_id, c.name, month_start
                ORDER BY category_name, month_start
                """);
        query.setParameter("orgId", orgId);
        query.setParameter("from", from);
        query.setParameter("to", to);

        List<Object[]> rows = query.getResultList();
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<String, Map<YearMonth, BigDecimal>> trendByCategory = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String categoryName = (String) row[1];
            trendByCategory.computeIfAbsent(categoryName, ignored -> initializeMonthMap(monthSequence));

            Object monthValue = row[2];
            if (monthValue != null) {
                LocalDate monthStart = ((java.sql.Date) monthValue).toLocalDate();
                trendByCategory.get(categoryName).put(YearMonth.from(monthStart), asBigDecimal(row[3]));
            }
        }

        List<TrendReport.CategoryTrend> trends = new ArrayList<>();
        for (Map.Entry<String, Map<YearMonth, BigDecimal>> entry : trendByCategory.entrySet()) {
            List<TrendReport.MonthlyPoint> points = monthSequence.stream()
                    .map(month -> TrendReport.MonthlyPoint.builder()
                            .yearMonth(month.format(YEAR_MONTH_FORMATTER))
                            .total(entry.getValue().getOrDefault(month, BigDecimal.ZERO))
                            .build())
                    .toList();
            trends.add(TrendReport.CategoryTrend.builder()
                    .categoryName(entry.getKey())
                    .trajectory(points)
                    .build());
        }
        return trends;
    }

    private Map<YearMonth, BigDecimal> initializeMonthMap(List<YearMonth> monthSequence) {
        Map<YearMonth, BigDecimal> map = new LinkedHashMap<>();
        for (YearMonth month : monthSequence) {
            map.put(month, BigDecimal.ZERO);
        }
        return map;
    }

    private List<YearMonth> buildMonthSequence(YearMonth start, YearMonth end) {
        List<YearMonth> months = new ArrayList<>();
        YearMonth cursor = start;
        while (!cursor.isAfter(end)) {
            months.add(cursor);
            cursor = cursor.plusMonths(1);
        }
        return months;
    }

    private BigDecimal getTotalForRange(UUID orgId, LocalDate from, LocalDate to) {
        Query query = entityManager.createNativeQuery("""
                SELECT COALESCE(SUM(total), 0)
                FROM mv_daily_spend
                WHERE org_id = :orgId
                  AND expense_date BETWEEN :from AND :to
                """);
        query.setParameter("orgId", orgId);
        query.setParameter("from", from);
        query.setParameter("to", to);
        Object value = query.getSingleResult();
        return asBigDecimal(value);
    }

    private BigDecimal calculatePctChange(BigDecimal current, BigDecimal baseline) {
        if (baseline == null || baseline.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return current.subtract(baseline)
                .multiply(BigDecimal.valueOf(100))
                .divide(baseline, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal asBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(value.toString());
    }
}
