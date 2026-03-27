package in.spendsmart.analyticsservice.controller;

import in.spendsmart.analyticsservice.entity.Budget;
import in.spendsmart.analyticsservice.service.BudgetService;
import in.spendsmart.analyticsservice.service.CreateBudgetRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    @PostMapping("/")
    public ResponseEntity<Budget> createBudget(
            @RequestHeader("X-Org-Id") UUID orgId,
            @RequestBody CreateBudgetRequest request
    ) {
        return ResponseEntity.ok(budgetService.createBudget(request, orgId));
    }

    @GetMapping("/")
    public ResponseEntity<List<Budget>> listActiveBudgets(@RequestHeader("X-Org-Id") UUID orgId) {
        return ResponseEntity.ok(budgetService.listActiveBudgets(orgId));
    }

    @GetMapping("/{id}/utilization")
    public ResponseEntity<Map<String, BigDecimal>> getUtilization(
            @RequestHeader("X-Org-Id") UUID orgId,
            @PathVariable("id") UUID budgetId
    ) {
        // orgId header is read for tenant context; utilization is looked up by budget id.
        return ResponseEntity.ok(Map.of("pctUsed", budgetService.getUtilization(budgetId)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Budget> deactivateBudget(
            @RequestHeader("X-Org-Id") UUID orgId,
            @PathVariable("id") UUID budgetId
    ) {
        return ResponseEntity.ok(budgetService.deactivateBudget(budgetId, orgId));
    }
}
