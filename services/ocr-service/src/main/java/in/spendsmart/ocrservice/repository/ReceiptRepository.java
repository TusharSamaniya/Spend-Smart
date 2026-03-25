package in.spendsmart.ocrservice.repository;

import in.spendsmart.ocrservice.entity.Receipt;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReceiptRepository extends JpaRepository<Receipt, UUID> {

    Optional<Receipt> findByOrgIdAndId(UUID orgId, UUID id);

    List<Receipt> findByDuplicateOf(UUID originalId);
}
