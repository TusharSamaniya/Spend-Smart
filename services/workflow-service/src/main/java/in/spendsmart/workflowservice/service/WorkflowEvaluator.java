package in.spendsmart.workflowservice.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.spendsmart.workflowservice.entity.WorkflowDefinition;
import in.spendsmart.workflowservice.repository.WorkflowDefinitionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorkflowEvaluator {

    private final WorkflowDefinitionRepository workflowDefinitionRepository;
    private final ObjectMapper objectMapper;

    public Optional<WorkflowDefinition> matchWorkflow(UUID orgId, BigDecimal amount, UUID categoryId, UUID teamId) {
        List<WorkflowDefinition> workflows = workflowDefinitionRepository.findByOrgIdAndIsActiveTrue(orgId);

        for (WorkflowDefinition workflow : workflows) {
            Optional<WorkflowCondition> parsed = parseCondition(workflow.getConditions());
            if (parsed.isEmpty()) {
                continue;
            }

            if (matches(parsed.get(), amount, categoryId, teamId)) {
                return Optional.of(workflow);
            }
        }

        return Optional.empty();
    }

    private Optional<WorkflowCondition> parseCondition(String conditionJson) {
        try {
            return Optional.of(objectMapper.readValue(conditionJson, WorkflowCondition.class));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private boolean matches(WorkflowCondition condition, BigDecimal amount, UUID categoryId, UUID teamId) {
        if (condition.getAmountGt() != null) {
            if (amount == null || amount.compareTo(condition.getAmountGt()) <= 0) {
                return false;
            }
        }

        if (isFilterSet(condition.getCategoryIds())) {
            if (categoryId == null || !condition.getCategoryIds().contains(categoryId)) {
                return false;
            }
        }

        if (isFilterSet(condition.getTeamIds())) {
            if (teamId == null || !condition.getTeamIds().contains(teamId)) {
                return false;
            }
        }

        return true;
    }

    private boolean isFilterSet(List<UUID> filterValues) {
        return filterValues != null && !filterValues.isEmpty();
    }

    @Data
    private static class WorkflowCondition {

        @JsonProperty("amount_gt")
        private BigDecimal amountGt;

        @JsonProperty("category_ids")
        private List<UUID> categoryIds;

        @JsonProperty("team_ids")
        private List<UUID> teamIds;
    }
}
