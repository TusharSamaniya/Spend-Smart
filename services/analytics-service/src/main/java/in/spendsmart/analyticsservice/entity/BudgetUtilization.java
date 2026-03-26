package in.spendsmart.analyticsservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "mv_budget_utilization")
@Immutable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetUtilization {

    @Id
    @Column(name = "budget_id", nullable = false)
    private UUID budgetId;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "name")
    private String name;

    @Column(name = "budget_amount", precision = 14, scale = 2)
    private BigDecimal budgetAmount;

    @Column(name = "spent", precision = 14, scale = 2)
    private BigDecimal spent;

    @Column(name = "pct_used", precision = 7, scale = 2)
    private BigDecimal pctUsed;
}
