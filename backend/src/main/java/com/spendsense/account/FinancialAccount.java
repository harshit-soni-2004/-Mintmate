package com.spendsense.account;

import com.spendsense.document.DocumentAccountType;
import com.spendsense.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "financial_accounts")
public class FinancialAccount {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "institution_name", nullable = false, length = 100)
    private String institutionName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 32)
    private DocumentAccountType accountType;

    @Column(name = "account_number_last4", length = 4)
    private String accountNumberLast4;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "is_primary", nullable = false)
    private boolean primary = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FinancialAccount() {
    }

    public FinancialAccount(
            User user,
            String name,
            String institutionName,
            DocumentAccountType accountType,
            String accountNumberLast4,
            String currency,
            boolean isPrimary
    ) {
        this.id = UUID.randomUUID();
        this.user = Objects.requireNonNull(user, "User cannot be null");
        this.name = Objects.requireNonNull(name, "Name cannot be null").trim();
        this.institutionName = Objects.requireNonNull(institutionName, "Institution name cannot be null").trim();
        this.accountType = Objects.requireNonNull(accountType, "Account type cannot be null");
        this.accountNumberLast4 = accountNumberLast4 != null ? accountNumberLast4.trim() : null;
        this.currency = (currency != null && !currency.isBlank()) ? currency.trim().toUpperCase() : "INR";
        this.active = true;
        this.primary = isPrimary;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public String getName() { return name; }
    public String getInstitutionName() { return institutionName; }
    public DocumentAccountType getAccountType() { return accountType; }
    public String getAccountNumberLast4() { return accountNumberLast4; }
    public String getCurrency() { return currency; }
    public boolean isActive() { return active; }
    public boolean isPrimary() { return primary; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String name, String institutionName, DocumentAccountType accountType, String accountNumberLast4, boolean isPrimary) {
        this.name = Objects.requireNonNull(name, "Name cannot be null").trim();
        this.institutionName = Objects.requireNonNull(institutionName, "Institution name cannot be null").trim();
        this.accountType = Objects.requireNonNull(accountType, "Account type cannot be null");
        this.accountNumberLast4 = accountNumberLast4 != null ? accountNumberLast4.trim() : null;
        this.primary = isPrimary;
    }

    public void deactivate() {
        this.active = false;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
    }
}
