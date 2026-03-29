package in.spendsmart.expense.repository;

import in.spendsmart.expense.entity.Organization;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

	Optional<Organization> findByName(String name);
}
