package in.spendsmart.upiservice.repository;

import in.spendsmart.upiservice.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUpiIdsContaining(String upiId);
}
