package in.spendsmart.analyticsservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "mv_daily_spend")
@Immutable
@IdClass(DailySpendId.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailySpend {

    @Id
    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Id
    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Id
    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(name = "total", precision = 14, scale = 2)
    private BigDecimal total;

    @Column(name = "txn_count")
    private Long txnCount;
}
