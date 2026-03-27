package in.spendsmart.analyticsservice.scheduler;

import in.spendsmart.analyticsservice.service.BudgetService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ViewRefreshScheduler {

    private final EntityManager entityManager;
    private final BudgetService budgetService;

    @Scheduled(fixedDelay = 60000)
    public void refreshViews() {
        try {
            entityManager.createNativeQuery("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_daily_spend").executeUpdate();
        } catch (Exception exception) {
            log.error("Failed to refresh materialized view mv_daily_spend", exception);
        }

        try {
            entityManager.createNativeQuery("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_budget_utilization").executeUpdate();
        } catch (Exception exception) {
            log.error("Failed to refresh materialized view mv_budget_utilization", exception);
        }

        try {
            budgetService.checkBudgetAlerts();
        } catch (Exception exception) {
            log.error("Failed to run budget threshold checks", exception);
        }
    }
}
