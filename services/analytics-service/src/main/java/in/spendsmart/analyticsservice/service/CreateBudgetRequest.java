package in.spendsmart.analyticsservice.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBudgetRequest {

    private String name;
    private UUID categoryId;
    private UUID teamId;
    private BigDecimal amount;
    private String periodType;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer[] alertThresholds;
}
