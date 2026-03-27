package in.spendsmart.analyticsservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.spendsmart.analyticsservice.entity.Budget;
import in.spendsmart.analyticsservice.entity.BudgetUtilization;
import in.spendsmart.analyticsservice.repository.BudgetRepository;
import in.spendsmart.analyticsservice.repository.BudgetUtilizationRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class BudgetService {

    private static final Integer[] DEFAULT_THRESHOLDS = new Integer[]{75, 90, 100};

    private final BudgetRepository budgetRepository;
    private final BudgetUtilizationRepository budgetUtilizationRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final SnsClient snsClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.sns.expense-events-topic-arn:${AWS_SNS_EXPENSE_EVENTS_TOPIC_ARN:arn:aws:sns:ap-south-1:000000000000:expense-events}}")
    private String expenseEventsTopicArn;

    public Budget createBudget(CreateBudgetRequest req, UUID orgId) {
        if (req == null || orgId == null) {
            throw new IllegalArgumentException("req and orgId are required");
        }
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be greater than 0");
        }
        if (req.getStartDate() == null || req.getEndDate() == null || req.getStartDate().isAfter(req.getEndDate())) {
            throw new IllegalArgumentException("startDate and endDate are required and startDate must be <= endDate");
        }

        Budget budget = Budget.builder()
                .id(UUID.randomUUID())
                .orgId(orgId)
                .name(req.getName())
                .categoryId(req.getCategoryId())
                .teamId(req.getTeamId())
                .amount(req.getAmount())
                .periodType(req.getPeriodType())
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
                .alertThresholds(normalizeThresholds(req.getAlertThresholds()))
                .createdAt(OffsetDateTime.now())
                .build();

        return budgetRepository.save(budget);
    }

    public BigDecimal getUtilization(UUID budgetId) {
        return budgetUtilizationRepository.findByBudgetId(budgetId)
                .map(BudgetUtilization::getPctUsed)
                .orElse(BigDecimal.ZERO);
    }

    public void checkBudgetAlerts() {
        LocalDate today = LocalDate.now();
        List<UUID> activeOrgIds = budgetRepository.findActiveOrgIds(today);
        for (UUID orgId : activeOrgIds) {
            try {
                checkBudgetAlerts(orgId);
            } catch (Exception exception) {
                log.error("Failed to check budget alerts for orgId={}", orgId, exception);
            }
        }
    }

    public void checkBudgetAlerts(UUID orgId) {
        if (orgId == null) {
            throw new IllegalArgumentException("orgId is required");
        }

        LocalDate today = LocalDate.now();
        List<Budget> activeBudgets = budgetRepository.findActiveByOrgId(orgId, today);
        if (activeBudgets.isEmpty()) {
            return;
        }

        Map<UUID, BudgetUtilization> utilizationByBudgetId = budgetUtilizationRepository.findByOrgId(orgId)
                .stream()
                .collect(Collectors.toMap(BudgetUtilization::getBudgetId, utilization -> utilization));

        for (Budget budget : activeBudgets) {
            BudgetUtilization utilization = utilizationByBudgetId.get(budget.getId());
            if (utilization == null) {
                continue;
            }

            BigDecimal pctUsed = defaultDecimal(utilization.getPctUsed());
            Integer[] thresholds = normalizeThresholds(budget.getAlertThresholds());

            for (Integer threshold : thresholds) {
                if (threshold == null) {
                    continue;
                }
                BigDecimal thresholdValue = BigDecimal.valueOf(threshold);
                if (pctUsed.compareTo(thresholdValue) < 0) {
                    continue;
                }

                String dedupeKey = String.format("budget_alert:%s:%d", budget.getId(), threshold);
                Boolean alreadySent = stringRedisTemplate.hasKey(dedupeKey);
                if (Boolean.TRUE.equals(alreadySent)) {
                    continue;
                }

                publishBudgetThresholdEvent(orgId, budget, utilization, threshold, pctUsed);
                Duration ttl = ttlUntilBudgetEnd(budget.getEndDate());
                if (!ttl.isNegative() && !ttl.isZero()) {
                    stringRedisTemplate.opsForValue().set(dedupeKey, "1", ttl);
                } else {
                    stringRedisTemplate.opsForValue().set(dedupeKey, "1", Duration.ofMinutes(5));
                }
            }
        }
    }

    private void publishBudgetThresholdEvent(
            UUID orgId,
            Budget budget,
            BudgetUtilization utilization,
            Integer threshold,
            BigDecimal pctUsed
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "budget.threshold_reached");
        payload.put("eventTime", OffsetDateTime.now());
        payload.put("orgId", orgId);
        payload.put("budgetId", budget.getId());
        payload.put("budgetName", budget.getName());
        payload.put("threshold", threshold);
        payload.put("pctUsed", pctUsed.setScale(2, RoundingMode.HALF_UP));
        payload.put("budgetAmount", defaultDecimal(utilization.getBudgetAmount()));
        payload.put("spent", defaultDecimal(utilization.getSpent()));

        PublishRequest request = PublishRequest.builder()
                .topicArn(expenseEventsTopicArn)
                .message(toJson(payload))
                .build();

        snsClient.publish(request);
        log.info("Published budget threshold alert orgId={} budgetId={} threshold={} pctUsed={}",
                orgId, budget.getId(), threshold, pctUsed);
    }

    private Integer[] normalizeThresholds(Integer[] thresholds) {
        Integer[] source = (thresholds == null || thresholds.length == 0) ? DEFAULT_THRESHOLDS : thresholds;
        return Arrays.stream(source)
                .filter(value -> value != null && value > 0)
                .sorted()
                .toArray(Integer[]::new);
    }

    private Duration ttlUntilBudgetEnd(LocalDate budgetEndDate) {
        if (budgetEndDate == null) {
            return Duration.ofMinutes(5);
        }
        var endExclusive = budgetEndDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        var now = java.time.Instant.now();
        return Duration.between(now, endExclusive);
    }

    private BigDecimal defaultDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize budget threshold payload", exception);
        }
    }
}
