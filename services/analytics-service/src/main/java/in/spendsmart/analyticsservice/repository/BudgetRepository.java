package in.spendsmart.analyticsservice.repository;

import in.spendsmart.analyticsservice.entity.Budget;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    List<Budget> findByOrgId(UUID orgId);

  Optional<Budget> findByIdAndOrgId(UUID id, UUID orgId);

    @Query("""
            select distinct b.orgId
            from Budget b
            where b.startDate <= :today
              and b.endDate >= :today
            """)
    List<UUID> findActiveOrgIds(@Param("today") LocalDate today);

    @Query("""
            select b
            from Budget b
            where b.orgId = :orgId
              and b.startDate <= :today
              and b.endDate >= :today
            """)
    List<Budget> findActiveByOrgId(@Param("orgId") UUID orgId, @Param("today") LocalDate today);
}
