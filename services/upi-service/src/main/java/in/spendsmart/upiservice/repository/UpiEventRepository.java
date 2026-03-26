package in.spendsmart.upiservice.repository;

import in.spendsmart.upiservice.entity.UpiEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UpiEventRepository extends JpaRepository<UpiEvent, UUID> {

    boolean existsByUpiRefId(String upiRefId);

    List<UpiEvent> findByUserId(UUID userId);
}
