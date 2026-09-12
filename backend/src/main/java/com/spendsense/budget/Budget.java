package com.spendsense.budget;

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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "budgets")
public class Budget {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TransactionCategory category;

    @Column(name = "limit_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal limitAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BudgetPeriod period;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Budget() {
    }

    public Budget(User user, TransactionCategory category, BigDecimal limitAmount,
                  BudgetPeriod period, LocalDate startDate, LocalDate endDate) {
        this.id = UUID.randomUUID();
        this.user = user;
        update(category, limitAmount, period, startDate, endDate);
    }

    public void update(TransactionCategory category, BigDecimal limitAmount,
                       BudgetPeriod period, LocalDate startDate, LocalDate endDate) {
        this.category = category;
        this.limitAmount = limitAmount;
        this.period = period != null ? period : BudgetPeriod.MONTHLY;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
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
    public TransactionCategory getCategory() { return category; }
    public BigDecimal getLimitAmount() { return limitAmount; }
    public BudgetPeriod getPeriod() { return period; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
