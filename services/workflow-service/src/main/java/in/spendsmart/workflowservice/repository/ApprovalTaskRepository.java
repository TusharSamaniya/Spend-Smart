package in.spendsmart.workflowservice.repository;

import in.spendsmart.workflowservice.entity.ApprovalTask;
import in.spendsmart.workflowservice.entity.ApprovalTask.TaskAction;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalTaskRepository extends JpaRepository<ApprovalTask, UUID> {

    List<ApprovalTask> findByExpenseId(UUID expenseId);

    List<ApprovalTask> findByApproverIdAndAction(UUID approverId, TaskAction action);

    List<ApprovalTask> findByActionAndDueAtBefore(TaskAction action, OffsetDateTime cutoff);

    List<ApprovalTask> findByExpenseIdAndStepNumber(UUID expenseId, int step);
}
