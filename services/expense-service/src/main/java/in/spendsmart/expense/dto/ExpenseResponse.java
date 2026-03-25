package in.spendsmart.expense.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
public class ExpenseResponse {

    private UUID id;
    private BigDecimal amount;
    private String currency;
    private BigDecimal amountBase;
    private String baseCurrency;
    private String merchantName;
    private String paymentMethod;
    private String status;
    private LocalDate expenseDate;
    private OffsetDateTime createdAt;
    private List<String> tags;
    private String notes;

    private CategoryInfo category;
    private GstInfo gst;

    public static record CategoryInfo(
            UUID id,
            String name,
            BigDecimal confidence
    ) {
    }

    public static record GstInfo(
            BigDecimal cgst,
            BigDecimal sgst,
            BigDecimal igst,
            BigDecimal total
    ) {
    }
}
