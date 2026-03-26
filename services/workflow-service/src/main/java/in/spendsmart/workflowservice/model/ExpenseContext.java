package in.spendsmart.workflowservice.model;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseContext {

    private UUID expenseId;
    private UUID orgId;
    private UUID submitterId;
}
