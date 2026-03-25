package in.spendsmart.expense.repository;

import in.spendsmart.expense.entity.Expense;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

    List<Expense> findByOrgIdAndUserIdOrderByExpenseDateDesc(UUID orgId, UUID userId);

    List<Expense> findByOrgIdAndStatusOrderByCreatedAtDesc(UUID orgId, Expense.ExpenseStatus status);

    @Query("""
            SELECT e
            FROM Expense e
            WHERE e.orgId = :orgId
              AND e.expenseDate BETWEEN :fromDate AND :toDate
            ORDER BY e.expenseDate DESC
            """)
    List<Expense> findByOrgAndDateRange(
            @Param("orgId") UUID orgId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    @Query("""
            SELECT e
            FROM Expense e
            WHERE e.id = :id
              AND e.deletedAt IS NULL
            """)
    Optional<Expense> findActiveById(@Param("id") UUID id);
}
