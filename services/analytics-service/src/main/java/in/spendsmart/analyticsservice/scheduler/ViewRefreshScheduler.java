package in.spendsmart.analyticsservice.scheduler;

import in.spendsmart.analyticsservice.service.BudgetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ViewRefreshScheduler {

    private final JdbcTemplate jdbcTemplate;
    private final BudgetService budgetService;

    @Scheduled(fixedDelay = 60000)
    public void refreshViews() {
        try {
            jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_daily_spend");
            log.info("Successfully refreshed materialized view mv_daily_spend");
        } catch (Exception exception) {
            log.error("Failed to refresh materialized view mv_daily_spend", exception);
        }

        try {
            jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY mv_budget_utilization");
            log.info("Successfully refreshed materialized view mv_budget_utilization");
        } catch (Exception exception) {
            log.error("Failed to refresh materialized view mv_budget_utilization", exception);
        }

        try {
            budgetService.checkBudgetAlerts();
            log.info("Completed budget threshold alert check cycle");
        } catch (Exception exception) {
            log.error("Failed to run budget threshold checks", exception);
        }
    }
}
