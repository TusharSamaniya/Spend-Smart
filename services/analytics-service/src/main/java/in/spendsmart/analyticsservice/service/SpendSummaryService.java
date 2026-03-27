package in.spendsmart.analyticsservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpendSummaryService {

    private static final Duration SUMMARY_CACHE_TTL = Duration.ofSeconds(60);

    private final EntityManager entityManager;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public SpendSummary getSummary(UUID orgId, LocalDate from, LocalDate to, String groupBy) {
        if (orgId == null || from == null || to == null) {
            throw new IllegalArgumentException("orgId, from and to are required");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from date must be on or before to date");
        }

        String normalizedGroupBy = groupBy == null ? "category" : groupBy.toLowerCase(Locale.ROOT);
        String cacheKey = String.format("summary:%s:%s:%s:%s", orgId, from, to, normalizedGroupBy);

        String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cachedJson != null && !cachedJson.isBlank()) {
            try {
                return objectMapper.readValue(cachedJson, SpendSummary.class);
            } catch (JsonProcessingException exception) {
                log.warn("Unable to deserialize cached spend summary for key {}", cacheKey, exception);
            }
        }

        Object[] totalsRow = getTotals(orgId, from, to);
        BigDecimal totalSpend = asBigDecimal(totalsRow[0]);
        long txnCount = asLong(totalsRow[1]);

        List<SpendSummary.CategorySpend> byCategory = getCategorySpend(orgId, from, to, totalSpend);
        List<SpendSummary.TopMerchantSpend> topMerchants = getTopMerchants(orgId, from, to);

        long daysInRange = ChronoUnit.DAYS.between(from, to) + 1;
        BigDecimal avgDailySpend = daysInRange > 0
                ? totalSpend.divide(BigDecimal.valueOf(daysInRange), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        SpendSummary summary = SpendSummary.builder()
                .totalSpend(totalSpend)
                .txnCount(txnCount)
                .byCategory(byCategory)
                .topMerchants(topMerchants)
                .avgDailySpend(avgDailySpend)
                .build();

        try {
            String payload = objectMapper.writeValueAsString(summary);
            stringRedisTemplate.opsForValue().set(cacheKey, payload, SUMMARY_CACHE_TTL);
        } catch (JsonProcessingException exception) {
            log.warn("Unable to serialize spend summary for cache key {}", cacheKey, exception);
        }

        return summary;
    }

    private Object[] getTotals(UUID orgId, LocalDate from, LocalDate to) {
        Query query = entityManager.createNativeQuery("""
                SELECT COALESCE(SUM(total), 0) AS total_spend,
                       COALESCE(SUM(txn_count), 0) AS txn_count
                FROM mv_daily_spend
                WHERE org_id = :orgId
                  AND expense_date BETWEEN :from AND :to
                """);
        query.setParameter("orgId", orgId);
        query.setParameter("from", from);
        query.setParameter("to", to);
        return (Object[]) query.getSingleResult();
    }

    @SuppressWarnings("unchecked")
    private List<SpendSummary.CategorySpend> getCategorySpend(UUID orgId, LocalDate from, LocalDate to, BigDecimal totalSpend) {
        Query query = entityManager.createNativeQuery("""
                SELECT COALESCE(c.name, 'Uncategorized') AS category_name,
                       COALESCE(SUM(ds.total), 0) AS total,
                       COALESCE(SUM(ds.txn_count), 0) AS txn_count
                FROM mv_daily_spend ds
                LEFT JOIN categories c ON c.id = ds.category_id
                WHERE ds.org_id = :orgId
                  AND ds.expense_date BETWEEN :from AND :to
                GROUP BY c.name
                ORDER BY total DESC
                """);
        query.setParameter("orgId", orgId);
        query.setParameter("from", from);
        query.setParameter("to", to);

        List<Object[]> rows = query.getResultList();
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }

        return rows.stream()
                .map(row -> {
                    BigDecimal categoryTotal = asBigDecimal(row[1]);
                    BigDecimal percentage = totalSpend.compareTo(BigDecimal.ZERO) > 0
                            ? categoryTotal.multiply(BigDecimal.valueOf(100))
                                    .divide(totalSpend, 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    return SpendSummary.CategorySpend.builder()
                            .categoryName((String) row[0])
                            .total(categoryTotal)
                            .count(asLong(row[2]))
                            .percentage(percentage)
                            .build();
                })
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<SpendSummary.TopMerchantSpend> getTopMerchants(UUID orgId, LocalDate from, LocalDate to) {
        Query query = entityManager.createNativeQuery("""
                SELECT COALESCE(NULLIF(e.merchant_name, ''), 'Unknown') AS merchant_name,
                       COALESCE(SUM(e.amount_base), 0) AS total,
                       COUNT(*) AS txn_count
                FROM expenses e
                WHERE e.org_id = :orgId
                  AND e.expense_date BETWEEN :from AND :to
                  AND e.deleted_at IS NULL
                  AND e.status IN ('APPROVED', 'REIMBURSED')
                GROUP BY 1
                ORDER BY total DESC
                LIMIT 10
                """);
        query.setParameter("orgId", orgId);
        query.setParameter("from", from);
        query.setParameter("to", to);

        List<Object[]> rows = query.getResultList();
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }

        return rows.stream()
                .map(row -> SpendSummary.TopMerchantSpend.builder()
                        .merchantName((String) row[0])
                        .total(asBigDecimal(row[1]))
                        .count(asLong(row[2]))
                        .build())
                .toList();
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

    private long asLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}
