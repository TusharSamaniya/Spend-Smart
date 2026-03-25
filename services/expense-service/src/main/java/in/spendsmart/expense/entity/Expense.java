package in.spendsmart.expense.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "expenses")
@SQLRestriction("deleted_at IS NULL")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Expense {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "org_id")
    private UUID orgId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "amount", precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "amount_base", precision = 14, scale = 2)
    private BigDecimal amountBase;

    @Column(name = "base_currency", length = 3)
    private String baseCurrency;

    @Column(name = "fx_rate", precision = 18, scale = 6)
    private BigDecimal fxRate;

    @Column(name = "category_id")
    private UUID categoryId;

    @Column(name = "category_confidence", precision = 5, scale = 4)
    private BigDecimal categoryConfidence;

    @Column(name = "merchant_name")
    private String merchantName;

    @Column(name = "merchant_vpa")
    private String merchantVpa;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private ExpenseStatus status;

    @Column(name = "receipt_id")
    private UUID receiptId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "gstin_supplier", length = 15)
    private String gstinSupplier;

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

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", columnDefinition = "text[]")
    private String[] tags;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public enum PaymentMethod {
        UPI,
        CARD,
        CASH,
        NEFT,
        WALLET
    }

    public enum ExpenseStatus {
        DRAFT,
        PENDING_APPROVAL,
        APPROVED,
        REJECTED,
        REIMBURSED
    }
}
