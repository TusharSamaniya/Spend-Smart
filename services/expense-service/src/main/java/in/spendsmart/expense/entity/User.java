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
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "org_id")
    private UUID orgId;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "phone", length = 15)
    private String phone;

    @Column(name = "role", length = 20)
    private String role;

    @Column(name = "upi_ids", columnDefinition = "text[]")
    @Builder.Default
    private String[] upiIds = new String[0];

    @Column(name = "default_currency", columnDefinition = "char(3)")
    private String defaultCurrency;

    @Column(name = "device_token", length = 512)
    private String deviceToken;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
