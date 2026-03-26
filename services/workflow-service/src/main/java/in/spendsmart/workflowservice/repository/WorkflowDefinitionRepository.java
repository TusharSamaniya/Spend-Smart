package in.spendsmart.workflowservice.repository;

import in.spendsmart.workflowservice.entity.WorkflowDefinition;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, UUID> {

    List<WorkflowDefinition> findByOrgIdAndIsActiveTrue(UUID orgId);

    Optional<WorkflowDefinition> findByIdAndOrgId(UUID id, UUID orgId);
}
