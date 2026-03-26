package in.spendsmart.workflowservice.model;

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
public class ExpenseSubmittedEvent {

    private UUID expenseId;
    private UUID orgId;
    private UUID submitterId;
    private BigDecimal amount;
    private UUID categoryId;
    private UUID teamId;
}
