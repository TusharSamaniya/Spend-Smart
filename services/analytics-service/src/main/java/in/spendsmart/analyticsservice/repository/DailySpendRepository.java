package in.spendsmart.analyticsservice.repository;

import in.spendsmart.analyticsservice.entity.DailySpend;
import in.spendsmart.analyticsservice.entity.DailySpendId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DailySpendRepository extends JpaRepository<DailySpend, DailySpendId> {

    List<DailySpend> findByOrgIdAndExpenseDateBetween(UUID orgId, LocalDate from, LocalDate to);

    @Query("""
            select d.categoryId as categoryId,
                   coalesce(sum(d.total), 0) as totalAmount
            from DailySpend d
            where d.orgId = :orgId
              and d.expenseDate between :from and :to
            group by d.categoryId
            """)
    List<CategorySpendSummary> findCategorySummaryByOrgIdAndExpenseDateBetween(
            @Param("orgId") UUID orgId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    interface CategorySpendSummary {
        UUID getCategoryId();

        BigDecimal getTotalAmount();
    }
}
