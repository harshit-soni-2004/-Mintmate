package com.spendsense.goal;

import com.spendsense.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "goals")
public class Goal {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "target_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal targetAmount;

    @Column(name = "current_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal currentAmount;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Goal() {
    }

    public Goal(User user, String name, BigDecimal targetAmount, BigDecimal currentAmount, LocalDate targetDate) {
        this.id = UUID.randomUUID();
        this.user = user;
        update(name, targetAmount, currentAmount, targetDate);
    }

    public void update(String name, BigDecimal targetAmount, BigDecimal currentAmount, LocalDate targetDate) {
        this.name = name;
        this.targetAmount = targetAmount;
        this.currentAmount = currentAmount != null ? currentAmount : BigDecimal.ZERO;
        this.targetDate = targetDate;
    }

    public void addContribution(BigDecimal amount) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            this.currentAmount = this.currentAmount.add(amount);
        }
    }

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (currentAmount == null) currentAmount = BigDecimal.ZERO;
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
    public BigDecimal getTargetAmount() { return targetAmount; }
    public BigDecimal getCurrentAmount() { return currentAmount; }
    public LocalDate getTargetDate() { return targetDate; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
