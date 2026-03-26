package in.spendsmart.workflowservice.service;

import in.spendsmart.workflowservice.entity.DelegationRule;
import in.spendsmart.workflowservice.repository.DelegationRuleRepository;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ApproverResolver {

    private final JdbcTemplate jdbcTemplate;
    private final DelegationRuleRepository delegationRuleRepository;

    public Optional<UUID> resolveApprover(WorkflowStep step, UUID orgId, UUID submitterId) {
        if (step == null || step.getApproverType() == null) {
            return Optional.empty();
        }

        Optional<UUID> resolvedApprover = switch (step.getApproverType().toUpperCase(Locale.ROOT)) {
            case "ROLE" -> resolveByRole(orgId, step.getRole());
            case "USER" -> Optional.ofNullable(step.getUserId());
            case "MANAGER_OF_SUBMITTER" -> resolveManagerOfSubmitter(orgId, submitterId);
            default -> Optional.empty();
        };

        return resolvedApprover.map(this::resolveDelegation);
    }

    private Optional<UUID> resolveByRole(UUID orgId, String role) {
        if (orgId == null || role == null || role.isBlank()) {
            return Optional.empty();
        }

        String sql = "SELECT id FROM users WHERE org_id = ? AND lower(role) = lower(?) LIMIT 1";
        return querySingleUuid(sql, orgId, role);
    }

    private Optional<UUID> resolveManagerOfSubmitter(UUID orgId, UUID submitterId) {
        if (orgId == null || submitterId == null) {
            return Optional.empty();
        }

        Optional<UUID> fromRelationshipTable = querySingleUuid(
                "SELECT manager_id FROM manager_relationships WHERE org_id = ? AND user_id = ? LIMIT 1",
                orgId,
                submitterId
        );
        if (fromRelationshipTable.isPresent()) {
            return fromRelationshipTable;
        }

        Optional<UUID> fromAlternateRelationshipColumns = querySingleUuid(
                "SELECT manager_id FROM manager_relationships WHERE org_id = ? AND employee_id = ? LIMIT 1",
                orgId,
                submitterId
        );
        if (fromAlternateRelationshipColumns.isPresent()) {
            return fromAlternateRelationshipColumns;
        }

        return querySingleUuid(
                "SELECT manager_id FROM users WHERE org_id = ? AND id = ? LIMIT 1",
                orgId,
                submitterId
        );
    }

    private Optional<UUID> querySingleUuid(String sql, Object... args) {
        try {
            return jdbcTemplate.query(sql, rs -> {
                if (rs.next()) {
                    return Optional.ofNullable((UUID) rs.getObject(1));
                }
                return Optional.<UUID>empty();
            }, args);
        } catch (DataAccessException exception) {
            return Optional.empty();
        }
    }

    private UUID resolveDelegation(UUID approverId) {
        LocalDate today = LocalDate.now();
        return delegationRuleRepository.findByDelegatorIdAndIsActiveTrue(approverId).stream()
                .filter(this::isDateWindowActive)
                .map(DelegationRule::getDelegateId)
                .findFirst()
                .orElse(approverId);
    }

    private boolean isDateWindowActive(DelegationRule rule) {
        LocalDate today = LocalDate.now();
        return rule.getValidFrom() != null
                && rule.getValidUntil() != null
                && !today.isBefore(rule.getValidFrom())
                && !today.isAfter(rule.getValidUntil());
    }
}
