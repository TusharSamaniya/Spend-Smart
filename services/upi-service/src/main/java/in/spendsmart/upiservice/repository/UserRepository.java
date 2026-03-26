package in.spendsmart.upiservice.repository;

import in.spendsmart.upiservice.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUpiIdsContaining(String upiId);

    @Modifying
    @Transactional
    @Query(value = "UPDATE users SET upi_ids = array_append(upi_ids, :upiId) WHERE id = :userId", nativeQuery = true)
    int appendUpiId(@Param("userId") UUID userId, @Param("upiId") String upiId);

    @Modifying
    @Transactional
    @Query(value = "UPDATE users SET upi_ids = array_remove(upi_ids, :upiId) WHERE id = :userId", nativeQuery = true)
    int removeUpiId(@Param("userId") UUID userId, @Param("upiId") String upiId);
}
