package in.spendsmart.analyticsservice.repository;

import in.spendsmart.analyticsservice.entity.BudgetUtilization;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetUtilizationRepository extends JpaRepository<BudgetUtilization, UUID> {

    List<BudgetUtilization> findByOrgId(UUID orgId);

    Optional<BudgetUtilization> findByBudgetId(UUID budgetId);
}
