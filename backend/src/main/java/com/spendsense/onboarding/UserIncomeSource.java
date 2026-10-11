package com.spendsense.onboarding;

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
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "user_income_sources")
public class UserIncomeSource {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "source_name", nullable = false, length = 100)
    private String sourceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "income_type", nullable = false, length = 32)
    private IncomeType incomeType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IncomeFrequency frequency;

    @Column(name = "declared_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal declaredAmount;

    @Column(name = "expected_day_of_month")
    private Integer expectedDayOfMonth;

    @Column(name = "employer_or_payer_keyword", length = 100)
    private String employerOrPayerKeyword;

    @Column(name = "is_primary", nullable = false)
    private boolean primary = true;

    @Column(name = "is_variable", nullable = false)
    private boolean variable = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserIncomeSource() {}

    public UserIncomeSource(User user, String sourceName, IncomeType incomeType, IncomeFrequency frequency,
                            BigDecimal declaredAmount, Integer expectedDayOfMonth, String employerOrPayerKeyword,
                            boolean primary, boolean variable) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.sourceName = sourceName;
        this.incomeType = incomeType;
        this.frequency = frequency;
        this.declaredAmount = declaredAmount;
        this.expectedDayOfMonth = expectedDayOfMonth;
        this.employerOrPayerKeyword = employerOrPayerKeyword;
        this.primary = primary;
        this.variable = variable;
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

    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }

    public IncomeType getIncomeType() { return incomeType; }
    public void setIncomeType(IncomeType incomeType) { this.incomeType = incomeType; }

    public IncomeFrequency getFrequency() { return frequency; }
    public void setFrequency(IncomeFrequency frequency) { this.frequency = frequency; }

    public BigDecimal getDeclaredAmount() { return declaredAmount; }
    public void setDeclaredAmount(BigDecimal declaredAmount) { this.declaredAmount = declaredAmount; }

    public Integer getExpectedDayOfMonth() { return expectedDayOfMonth; }
    public void setExpectedDayOfMonth(Integer expectedDayOfMonth) { this.expectedDayOfMonth = expectedDayOfMonth; }

    public String getEmployerOrPayerKeyword() { return employerOrPayerKeyword; }
    public void setEmployerOrPayerKeyword(String employerOrPayerKeyword) { this.employerOrPayerKeyword = employerOrPayerKeyword; }

    public boolean isPrimary() { return primary; }
    public void setPrimary(boolean primary) { this.primary = primary; }

    public boolean isVariable() { return variable; }
    public void setVariable(boolean variable) { this.variable = variable; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
