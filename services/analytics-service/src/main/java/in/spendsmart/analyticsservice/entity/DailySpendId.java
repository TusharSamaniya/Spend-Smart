package in.spendsmart.analyticsservice.entity;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailySpendId implements Serializable {

    private UUID orgId;
    private UUID categoryId;
    private LocalDate expenseDate;
}
