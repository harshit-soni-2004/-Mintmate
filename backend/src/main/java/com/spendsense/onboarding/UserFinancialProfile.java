package com.spendsense.onboarding;

import com.spendsense.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "user_financial_profiles")
public class UserFinancialProfile {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserPersona persona;

    @Column(name = "monthly_savings_target", precision = 12, scale = 2)
    private BigDecimal monthlySavingsTarget;

    @Column(name = "monthly_investment_target", precision = 12, scale = 2)
    private BigDecimal monthlyInvestmentTarget;

    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserFinancialProfile() {}

    public UserFinancialProfile(User user, UserPersona persona, BigDecimal monthlySavingsTarget, BigDecimal monthlyInvestmentTarget) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.persona = persona;
        this.monthlySavingsTarget = monthlySavingsTarget;
        this.monthlyInvestmentTarget = monthlyInvestmentTarget;
        this.onboardingCompleted = true;
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
    public void setUser(User user) { this.user = user; }

    public UserPersona getPersona() { return persona; }
    public void setPersona(UserPersona persona) { this.persona = persona; }

    public BigDecimal getMonthlySavingsTarget() { return monthlySavingsTarget; }
    public void setMonthlySavingsTarget(BigDecimal monthlySavingsTarget) { this.monthlySavingsTarget = monthlySavingsTarget; }

    public BigDecimal getMonthlyInvestmentTarget() { return monthlyInvestmentTarget; }
    public void setMonthlyInvestmentTarget(BigDecimal monthlyInvestmentTarget) { this.monthlyInvestmentTarget = monthlyInvestmentTarget; }

    public boolean isOnboardingCompleted() { return onboardingCompleted; }
    public void setOnboardingCompleted(boolean onboardingCompleted) { this.onboardingCompleted = onboardingCompleted; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
