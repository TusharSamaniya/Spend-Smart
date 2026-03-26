package in.spendsmart.workflowservice.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.spendsmart.workflowservice.entity.WorkflowDefinition;
import in.spendsmart.workflowservice.repository.WorkflowDefinitionRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/workflows")
@RequiredArgsConstructor
@Validated
public class WorkflowController {

    private final WorkflowDefinitionRepository workflowDefinitionRepository;
    private final ObjectMapper objectMapper;

    @PostMapping("/")
    public ResponseEntity<WorkflowDefinition> createWorkflow(
            @RequestHeader("X-Org-Id") UUID currentOrgId,
            @Valid @RequestBody CreateWorkflowRequest request
    ) {
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .orgId(currentOrgId)
                .name(request.name())
                .conditions(toJson(request.conditions()))
                .steps(toJson(request.steps()))
                .isActive(true)
                .build();

        return ResponseEntity.ok(workflowDefinitionRepository.save(workflow));
    }

    @GetMapping("/")
    public ResponseEntity<List<WorkflowDefinition>> listActiveWorkflows(@RequestHeader("X-Org-Id") UUID currentOrgId) {
        return ResponseEntity.ok(workflowDefinitionRepository.findByOrgIdAndIsActiveTrue(currentOrgId));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<WorkflowDefinition> updateWorkflow(
            @RequestHeader("X-Org-Id") UUID currentOrgId,
            @PathVariable UUID id,
            @RequestBody UpdateWorkflowRequest request
    ) {
        WorkflowDefinition workflow = workflowDefinitionRepository.findByIdAndOrgId(id, currentOrgId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow definition not found"));

        if (request.name() != null && !request.name().isBlank()) {
            workflow.setName(request.name());
        }
        if (request.conditions() != null) {
            workflow.setConditions(toJson(request.conditions()));
        }
        if (request.steps() != null) {
            workflow.setSteps(toJson(request.steps()));
        }
        if (request.active() != null) {
            workflow.setIsActive(request.active());
        }

        return ResponseEntity.ok(workflowDefinitionRepository.save(workflow));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> softDeleteWorkflow(
            @RequestHeader("X-Org-Id") UUID currentOrgId,
            @PathVariable UUID id
    ) {
        WorkflowDefinition workflow = workflowDefinitionRepository.findByIdAndOrgId(id, currentOrgId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow definition not found"));

        workflow.setIsActive(false);
        workflowDefinitionRepository.save(workflow);
        return ResponseEntity.noContent().build();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to serialize workflow JSON", exception);
        }
    }

    public record CreateWorkflowRequest(
            @NotBlank String name,
            @NotNull Map<String, Object> conditions,
            @NotNull List<Map<String, Object>> steps
    ) {
    }

    public record UpdateWorkflowRequest(
            String name,
            Map<String, Object> conditions,
            List<Map<String, Object>> steps,
            Boolean active
    ) {
    }
}
