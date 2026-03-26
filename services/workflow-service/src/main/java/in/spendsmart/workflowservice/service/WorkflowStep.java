package in.spendsmart.workflowservice.service;

import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowStep {

    private Integer stepNumber;
    private String approverType;
    private String role;
    private UUID userId;
    private BigDecimal thresholdAbove;
}
