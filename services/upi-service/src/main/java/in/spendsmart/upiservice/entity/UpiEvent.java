package in.spendsmart.upiservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "upi_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpiEvent {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "org_id")
    private UUID orgId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "upi_ref_id", length = 64, nullable = false, unique = true)
    private String upiRefId;

    @Column(name = "vpa_sender", length = 128)
    private String vpaSender;

    @Column(name = "vpa_receiver", length = 128)
    private String vpaReceiver;

    @Column(name = "amount", precision = 14, scale = 2, nullable = false)
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "txn_timestamp", nullable = false)
    private OffsetDateTime txnTimestamp;

    @Column(name = "resolved_merchant", length = 255)
    private String resolvedMerchant;

    @Column(name = "expense_id")
    private UUID expenseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 20)
    private UpiSource source;

    @Column(name = "raw_payload", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String rawPayload;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public enum UpiSource {
        AA_FRAMEWORK,
        WEBHOOK,
        SMS_PARSE
    }
}
