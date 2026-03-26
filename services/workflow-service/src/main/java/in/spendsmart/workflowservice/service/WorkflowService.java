package in.spendsmart.workflowservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.spendsmart.workflowservice.client.ExpenseServiceClient;
import in.spendsmart.workflowservice.client.ExpenseServiceClient.ExpenseStatus;
import in.spendsmart.workflowservice.client.NotificationServiceClient;
import in.spendsmart.workflowservice.entity.ApprovalTask;
import in.spendsmart.workflowservice.entity.ApprovalTask.TaskAction;
import in.spendsmart.workflowservice.entity.WorkflowDefinition;
import in.spendsmart.workflowservice.model.ExpenseContext;
import in.spendsmart.workflowservice.model.ExpenseSubmittedEvent;
import in.spendsmart.workflowservice.repository.ApprovalTaskRepository;
import in.spendsmart.workflowservice.repository.WorkflowDefinitionRepository;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@Transactional
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowEvaluator workflowEvaluator;
    private final ApproverResolver approverResolver;
    private final WorkflowDefinitionRepository workflowDefinitionRepository;
    private final ApprovalTaskRepository approvalTaskRepository;
    private final ExpenseServiceClient expenseServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final ObjectMapper objectMapper;

    public void onExpenseSubmitted(ExpenseSubmittedEvent event) {
        Optional<WorkflowDefinition> workflowMatch = workflowEvaluator.matchWorkflow(
                event.orgId(),
                event.amount(),
                event.categoryId(),
                event.teamId()
        );

        if (workflowMatch.isEmpty()) {
            expenseServiceClient.updateStatus(event.expenseId(), ExpenseStatus.APPROVED);
            return;
        }

        WorkflowDefinition workflow = workflowMatch.get();
        WorkflowStep firstStep = getOrderedSteps(workflow).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Matched workflow has no steps configured"));

        UUID approverId = approverResolver
            .resolveApprover(firstStep, event.orgId(), event.userId())
                .orElseThrow(() -> new IllegalStateException("Unable to resolve approver for first workflow step"));

        ApprovalTask task = createApprovalTask(
            event.expenseId(),
                workflow,
                firstStep,
                approverId
        );

        approvalTaskRepository.save(task);
        notificationServiceClient.sendApprovalRequest(approverId, event);
        expenseServiceClient.updateStatus(event.expenseId(), ExpenseStatus.PENDING_APPROVAL);
    }

    public void processApprovalAction(UUID taskId, TaskAction action, String comment, UUID actorId) {
        ApprovalTask task = approvalTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Approval task not found"));

        if (!task.getApproverId().equals(actorId)) {
            throw new IllegalStateException("Only the assigned approver can act on this task");
        }

        task.setAction(action);
        task.setComment(comment);
        task.setActedAt(Instant.now());
        approvalTaskRepository.save(task);

        if (action == TaskAction.REJECTED) {
            expenseServiceClient.updateStatus(task.getExpenseId(), ExpenseStatus.REJECTED);
            notificationServiceClient.notifyExpenseRejected(task.getExpenseId(), comment);
            return;
        }

        if (action != TaskAction.APPROVED) {
            return;
        }

        WorkflowDefinition workflow = workflowDefinitionRepository.findById(task.getWorkflowId())
                .orElseThrow(() -> new IllegalStateException("Workflow not found for approval task"));

        List<WorkflowStep> orderedSteps = getOrderedSteps(workflow);
        int nextStepNumber = task.getStepNumber() + 1;
        Optional<WorkflowStep> nextStep = orderedSteps.stream()
                .filter(step -> step.getStepNumber() != null && step.getStepNumber() == nextStepNumber)
                .findFirst();

        if (nextStep.isEmpty()) {
            expenseServiceClient.updateStatus(task.getExpenseId(), ExpenseStatus.APPROVED);
            return;
        }

        ExpenseContext expenseContext = expenseServiceClient.getExpenseContext(task.getExpenseId())
                .orElseThrow(() -> new IllegalStateException("Expense context not found for next-step resolution"));

        UUID nextApproverId = approverResolver
                .resolveApprover(nextStep.get(), expenseContext.getOrgId(), expenseContext.getSubmitterId())
                .orElseThrow(() -> new IllegalStateException("Unable to resolve approver for next workflow step"));

        ApprovalTask nextTask = createApprovalTask(task.getExpenseId(), workflow, nextStep.get(), nextApproverId);
        approvalTaskRepository.save(nextTask);
        notificationServiceClient.sendApprovalRequest(nextApproverId, expenseContext);
    }

    private ApprovalTask createApprovalTask(UUID expenseId, WorkflowDefinition workflow, WorkflowStep step, UUID approverId) {
        int escalationHours = workflow.getEscalationHours() != null ? workflow.getEscalationHours() : 48;

        return ApprovalTask.builder()
                .expenseId(expenseId)
                .workflowId(workflow.getId())
                .stepNumber(step.getStepNumber())
                .approverId(approverId)
                .action(TaskAction.PENDING)
                .dueAt(Instant.now().plus(escalationHours, ChronoUnit.HOURS))
                .build();
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
