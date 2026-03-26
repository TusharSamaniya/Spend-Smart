package in.spendsmart.workflowservice.scheduler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.spendsmart.workflowservice.client.ExpenseServiceClient.ExpenseDetails;
import in.spendsmart.workflowservice.client.ExpenseServiceClient;
import in.spendsmart.workflowservice.client.NotificationServiceClient;
import in.spendsmart.workflowservice.entity.ApprovalTask;
import in.spendsmart.workflowservice.entity.ApprovalTask.TaskAction;
import in.spendsmart.workflowservice.entity.WorkflowDefinition;
import in.spendsmart.workflowservice.repository.ApprovalTaskRepository;
import in.spendsmart.workflowservice.repository.WorkflowDefinitionRepository;
import in.spendsmart.workflowservice.service.ApproverResolver;
import in.spendsmart.workflowservice.service.WorkflowStep;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EscalationScheduler {

    private final ApprovalTaskRepository approvalTaskRepository;
    private final WorkflowDefinitionRepository workflowDefinitionRepository;
    private final ApproverResolver approverResolver;
    private final ExpenseServiceClient expenseServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 900000)
    public void checkOverdueTasks() {
        List<ApprovalTask> overdueTasks = approvalTaskRepository
                .findByActionAndDueAtBefore(TaskAction.PENDING, OffsetDateTime.now());

        for (ApprovalTask task : overdueTasks) {
            try {
                escalateTask(task);
            } catch (Exception exception) {
                log.error("Failed to escalate task {} for expense {}", task.getId(), task.getExpenseId(), exception);
            }
        }
    }

    private void escalateTask(ApprovalTask task) {
        task.setAction(TaskAction.ESCALATED);
        task.setActedAt(Instant.now());
        approvalTaskRepository.save(task);

        WorkflowDefinition workflow = workflowDefinitionRepository.findById(task.getWorkflowId())
                .orElseThrow(() -> new IllegalStateException("Workflow not found for task " + task.getId()));

        List<WorkflowStep> orderedSteps = getOrderedSteps(workflow);
        int nextStepNumber = task.getStepNumber() + 1;
        Optional<WorkflowStep> nextStep = orderedSteps.stream()
                .filter(step -> step.getStepNumber() != null && step.getStepNumber() == nextStepNumber)
                .findFirst();

        if (nextStep.isEmpty()) {
            log.info(
                    "No higher escalation step found for expense {} and approver {}",
                    task.getExpenseId(),
                    task.getApproverId()
            );
            return;
        }

        ExpenseDetails expenseDetails = expenseServiceClient.getExpenseDetails(task.getExpenseId());

        UUID nextApproverId = approverResolver
            .resolveApprover(nextStep.get(), expenseDetails.orgId(), expenseDetails.userId())
                .orElseThrow(() -> new IllegalStateException("Unable to resolve next approver for expense " + task.getExpenseId()));

        int escalationHours = workflow.getEscalationHours() != null ? workflow.getEscalationHours() : 48;
        ApprovalTask escalatedTask = ApprovalTask.builder()
                .expenseId(task.getExpenseId())
                .workflowId(task.getWorkflowId())
                .stepNumber(nextStep.get().getStepNumber())
                .approverId(nextApproverId)
                .action(TaskAction.PENDING)
                .dueAt(Instant.now().plus(escalationHours, ChronoUnit.HOURS))
                .build();

        approvalTaskRepository.save(escalatedTask);
        notificationServiceClient.sendEscalation(task.getApproverId(), nextApproverId, task.getExpenseId());

        log.info(
                "Escalated expense {} from approver {} to approver {}",
                task.getExpenseId(),
                task.getApproverId(),
                nextApproverId
        );
    }

    private List<WorkflowStep> getOrderedSteps(WorkflowDefinition workflow) {
        try {
            List<WorkflowStep> steps = objectMapper.readValue(
                    workflow.getSteps(),
                    new TypeReference<List<WorkflowStep>>() {
                    }
            );
            return steps.stream()
                    .sorted(Comparator.comparing(WorkflowStep::getStepNumber, Comparator.nullsLast(Integer::compareTo)))
                    .toList();
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid workflow steps JSON for workflow " + workflow.getId(), exception);
        }
    }
}
