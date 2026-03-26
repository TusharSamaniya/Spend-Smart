package in.spendsmart.workflowservice.repository;

import in.spendsmart.workflowservice.entity.DelegationRule;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DelegationRuleRepository extends JpaRepository<DelegationRule, UUID> {

    List<DelegationRule> findByDelegatorIdAndIsActiveTrue(UUID delegatorId);
}
