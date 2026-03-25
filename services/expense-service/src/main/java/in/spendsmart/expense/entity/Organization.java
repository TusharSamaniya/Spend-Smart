package in.spendsmart.expense.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "organizations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Organization {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "plan", length = 20)
    private String plan;

    @Column(name = "base_currency", length = 3)
    private String baseCurrency;

    @Column(name = "gstin", length = 15)
    private String gstin;

    @Column(name = "state_code", length = 2)
    private String stateCode;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
