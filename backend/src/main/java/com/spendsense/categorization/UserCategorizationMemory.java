package com.spendsense.categorization;

import com.spendsense.transaction.TransactionCategory;
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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "user_categorization_memories",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_cat_memory_user_merchant_cat",
                columnNames = {"user_id", "normalized_merchant", "category"}
        )
)
public class UserCategorizationMemory {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "normalized_merchant", nullable = false, length = 120)
    private String normalizedMerchant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TransactionCategory category;

    @Column(name = "usage_count", nullable = false)
    private int usageCount;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserCategorizationMemory() {
    }

    public UserCategorizationMemory(User user, String normalizedMerchant, TransactionCategory category) {
        this.id = UUID.randomUUID();
        this.user = Objects.requireNonNull(user, "User cannot be null");
        this.normalizedMerchant = Objects.requireNonNull(normalizedMerchant, "Normalized merchant cannot be null");
        this.category = Objects.requireNonNull(category, "Category cannot be null");
        this.usageCount = 1;
        Instant now = Instant.now();
        this.lastUsedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void recordUsage() {
        this.usageCount++;
        Instant now = Instant.now();
        this.lastUsedAt = now;
        this.updatedAt = now;
    }

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (lastUsedAt == null) {
            lastUsedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getNormalizedMerchant() {
        return normalizedMerchant;
    }

    public TransactionCategory getCategory() {
        return category;
    }

    public int getUsageCount() {
        return usageCount;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
