package in.spendsmart.workflowservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "approval_tasks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalTask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "expense_id", nullable = false)
    private UUID expenseId;

    @Column(name = "workflow_id")
    private UUID workflowId;

    @Column(name = "step_number", nullable = false)
    private Integer stepNumber;

    @Column(name = "approver_id", nullable = false)
    private UUID approverId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 20)
    private TaskAction action = TaskAction.PENDING;

    @Column(name = "comment")
    private String comment;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "acted_at")
    private Instant actedAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public enum TaskAction {
        PENDING,
        APPROVED,
        REJECTED,
        ESCALATED
    }
}
