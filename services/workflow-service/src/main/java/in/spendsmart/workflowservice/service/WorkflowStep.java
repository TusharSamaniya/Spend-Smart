package in.spendsmart.workflowservice.service;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonProperty("step_number")
    private Integer stepNumber;

    @JsonProperty("approver_type")
    private String approverType;

    private String role;

    @JsonProperty("user_id")
    private UUID userId;

    @JsonProperty("threshold_above")
    private BigDecimal thresholdAbove;
}
