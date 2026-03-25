package in.spendsmart.expense.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateExpenseRequest {

    @NotNull
    @Positive
    private BigDecimal amount;

    @NotBlank
    @Size(min = 3, max = 3)
    private String currency;

    private String merchantName;

    private String merchantVpa;

    @NotBlank
    private String paymentMethod;

    @NotNull
    private LocalDate expenseDate;

    private String notes;

    private UUID receiptId;

    private UUID projectId;

    private List<String> tags;
}
