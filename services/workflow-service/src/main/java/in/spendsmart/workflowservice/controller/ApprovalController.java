package in.spendsmart.workflowservice.controller;

import com.fasterxml.jackson.databind.JsonNode;
import in.spendsmart.workflowservice.entity.ApprovalTask;
import in.spendsmart.workflowservice.entity.ApprovalTask.TaskAction;
import in.spendsmart.workflowservice.entity.WorkflowDefinition;
import in.spendsmart.workflowservice.repository.ApprovalTaskRepository;
import in.spendsmart.workflowservice.repository.WorkflowDefinitionRepository;
import in.spendsmart.workflowservice.service.WorkflowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/approvals")
@RequiredArgsConstructor
@Validated
public class ApprovalController {

    private final WorkflowService workflowService;
    private final ApprovalTaskRepository approvalTaskRepository;
    private final WorkflowDefinitionRepository workflowDefinitionRepository;

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApprovalTask> approve(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID currentUserId,
            @RequestBody(required = false) ApprovalActionRequest request
    ) {
        String comment = request != null ? request.comment() : null;
        workflowService.processApprovalAction(id, TaskAction.APPROVED, comment, currentUserId);

        ApprovalTask updatedTask = approvalTaskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Approval task not found after update"));
        return ResponseEntity.ok(updatedTask);
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApprovalTask> reject(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") UUID currentUserId,
            @Valid @RequestBody RejectActionRequest request
    ) {
        workflowService.processApprovalAction(id, TaskAction.REJECTED, request.comment(), currentUserId);

        ApprovalTask updatedTask = approvalTaskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Approval task not found after update"));
        return ResponseEntity.ok(updatedTask);
    }

    @GetMapping("/pending")
    public ResponseEntity<List<ApprovalTask>> pending(@RequestHeader("X-User-Id") UUID currentUserId) {
        List<ApprovalTask> pendingTasks = approvalTaskRepository.findByApproverIdAndAction(currentUserId, TaskAction.PENDING);
        return ResponseEntity.ok(pendingTasks);
    }

    @GetMapping("/expense/{expenseId}")
    public ResponseEntity<List<ApprovalTask>> approvalHistory(@PathVariable UUID expenseId) {
        List<ApprovalTask> history = approvalTaskRepository.findByExpenseId(expenseId).stream()
                .sorted(Comparator
                        .comparing(ApprovalTask::getStepNumber, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ApprovalTask::getCreatedAt, Comparator.nullsLast(java.time.Instant::compareTo)))
                .toList();
        return ResponseEntity.ok(history);
    }

    @PostMapping("/workflows")
    public ResponseEntity<WorkflowDefinition> createWorkflow(
            @RequestHeader("X-Org-Id") UUID currentOrgId,
            @Valid @RequestBody CreateWorkflowRequest request
    ) {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .orgId(currentOrgId)
                .name(request.name())
                .conditions(request.conditions().toString())
                .steps(request.steps().toString())
                .isActive(true)
                .build();

        WorkflowDefinition savedWorkflow = workflowDefinitionRepository.save(workflow);
        return ResponseEntity.ok(savedWorkflow);
    }

    @GetMapping("/workflows")
    public ResponseEntity<List<WorkflowDefinition>> listActiveWorkflows(@RequestHeader("X-Org-Id") UUID currentOrgId) {
        List<WorkflowDefinition> activeWorkflows = workflowDefinitionRepository.findByOrgIdAndIsActiveTrue(currentOrgId);
        return ResponseEntity.ok(activeWorkflows);
    }

    public record ApprovalActionRequest(String comment) {
    }

    public record RejectActionRequest(@NotBlank String comment) {
    }

    public record CreateWorkflowRequest(
            @NotBlank String name,
            @NotNull JsonNode conditions,
            @NotNull JsonNode steps
    ) {
    }
}
