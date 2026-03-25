package in.spendsmart.ocrservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
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
@Table(name = "receipts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Receipt {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "s3_key", nullable = false)
    private String s3Key;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private ReceiptStatus status;

    @Column(name = "merchant_name", length = 255)
    private String merchantName;

    @Column(name = "amount", precision = 14, scale = 2)
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "receipt_date")
    private LocalDate receiptDate;

    @Column(name = "gst_amount", precision = 14, scale = 2)
    private BigDecimal gstAmount;

    @Column(name = "cgst", precision = 14, scale = 2)
    private BigDecimal cgst;

    @Column(name = "sgst", precision = 14, scale = 2)
    private BigDecimal sgst;

    @Column(name = "igst", precision = 14, scale = 2)
    private BigDecimal igst;

    @Column(name = "hsn_sac_code", length = 8)
    private String hsnSacCode;

    @Column(name = "line_items", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String lineItems;

    @Column(name = "confidence_score", precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @Column(name = "duplicate_of")
    private UUID duplicateOf;

    @Column(name = "raw_ocr", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String rawOcr;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
        if (status == null) {
            status = ReceiptStatus.UPLOADING;
        }
    }

    public enum ReceiptStatus {
        UPLOADING,
        PROCESSING,
        DONE,
        FAILED
    }
}
