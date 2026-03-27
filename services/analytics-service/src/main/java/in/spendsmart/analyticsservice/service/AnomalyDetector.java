package in.spendsmart.analyticsservice.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AnomalyDetector {

    private final EntityManager entityManager;

    @Value("${analytics.anomaly-threshold:1.20}")
    private BigDecimal anomalyThreshold;

    public List<Anomaly> detectAnomalies(UUID orgId, LocalDate periodStart, LocalDate periodEnd) {
        if (orgId == null || periodStart == null || periodEnd == null) {
            throw new IllegalArgumentException("orgId, periodStart and periodEnd are required");
        }
        if (periodStart.isAfter(periodEnd)) {
            throw new IllegalArgumentException("periodStart must be on or before periodEnd");
        }

        LocalDate baselineStart = periodStart.minusDays(90);
        LocalDate baselineEnd = periodStart.minusDays(1);

        Map<String, BaselineStats> baselineByCategory = getBaselineByCategory(orgId, baselineStart, baselineEnd);
        Map<String, BigDecimal> actualByCategory = getActualByCategory(orgId, periodStart, periodEnd);

        List<Anomaly> anomalies = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : actualByCategory.entrySet()) {
            String category = entry.getKey();
            BigDecimal actual = entry.getValue();

            BaselineStats baselineStats = baselineByCategory.get(category);
            if (baselineStats == null || baselineStats.averageDaily.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal periodBaseline = baselineStats.averageDaily.multiply(BigDecimal.valueOf(baselineStats.periodDays));
            BigDecimal thresholdAmount = periodBaseline.multiply(anomalyThreshold);

            if (actual.compareTo(thresholdAmount) > 0) {
                BigDecimal pctAbove = actual.subtract(periodBaseline)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(periodBaseline, 2, RoundingMode.HALF_UP);

                anomalies.add(Anomaly.builder()
                        .category(category)
                        .actual(actual)
                        .baseline(periodBaseline.setScale(2, RoundingMode.HALF_UP))
                        .pctAbove(pctAbove)
                        .build());
            }
        }

        anomalies.sort((a, b) -> b.getPctAbove().compareTo(a.getPctAbove()));
        return anomalies;
    }

    @SuppressWarnings("unchecked")
    private Map<String, BaselineStats> getBaselineByCategory(UUID orgId, LocalDate baselineStart, LocalDate baselineEnd) {
        Query query = entityManager.createNativeQuery("""
                SELECT COALESCE(c.name, 'Uncategorized') AS category_name,
                       COALESCE(SUM(ds.total), 0) AS baseline_total
                FROM mv_daily_spend ds
                LEFT JOIN categories c ON c.id = ds.category_id
                WHERE ds.org_id = :orgId
                  AND ds.expense_date BETWEEN :from AND :to
                GROUP BY category_name
                """);
        query.setParameter("orgId", orgId);
        query.setParameter("from", baselineStart);
        query.setParameter("to", baselineEnd);

        List<Object[]> rows = query.getResultList();
        long baselineDays = 90L;
        Map<String, BaselineStats> map = new HashMap<>();

        for (Object[] row : rows) {
            String category = (String) row[0];
            BigDecimal total = asBigDecimal(row[1]);
            BigDecimal avgDaily = total.divide(BigDecimal.valueOf(baselineDays), 6, RoundingMode.HALF_UP);
            map.put(category, new BaselineStats(avgDaily, baselineDays));
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private Map<String, BigDecimal> getActualByCategory(UUID orgId, LocalDate periodStart, LocalDate periodEnd) {
        Query query = entityManager.createNativeQuery("""
                SELECT COALESCE(c.name, 'Uncategorized') AS category_name,
                       COALESCE(SUM(ds.total), 0) AS actual_total
                FROM mv_daily_spend ds
                LEFT JOIN categories c ON c.id = ds.category_id
                WHERE ds.org_id = :orgId
                  AND ds.expense_date BETWEEN :from AND :to
                GROUP BY category_name
                """);
        query.setParameter("orgId", orgId);
        query.setParameter("from", periodStart);
        query.setParameter("to", periodEnd);

        List<Object[]> rows = query.getResultList();
        Map<String, BigDecimal> map = new HashMap<>();

        for (Object[] row : rows) {
            map.put((String) row[0], asBigDecimal(row[1]));
        }
        return map;
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

    private static class BaselineStats {
        private final BigDecimal averageDaily;
        private final long periodDays;

        private BaselineStats(BigDecimal averageDaily, long periodDays) {
            this.averageDaily = averageDaily;
            this.periodDays = periodDays;
        }
    }
}
