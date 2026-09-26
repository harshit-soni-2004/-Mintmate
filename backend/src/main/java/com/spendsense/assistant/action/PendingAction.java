package com.spendsense.assistant.action;

import com.spendsense.user.User;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assistant_pending_actions")
public class PendingAction {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "tool_name", nullable = false, length = 64)
    private String toolName;

    @Column(name = "parameters_json", nullable = false, columnDefinition = "TEXT")
    private String parametersJson;

    @Column(name = "summary_text", nullable = false, length = 500)
    private String summaryText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PendingActionStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "executed_at")
    private Instant executedAt;

    @Column(name = "failure_information", length = 1000)
    private String failureInformation;

    protected PendingAction() {}

    public PendingAction(
            User user,
            String toolName,
            String parametersJson,
            String summaryText,
            Instant expiresAt
    ) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.toolName = toolName;
        this.parametersJson = parametersJson;
        this.summaryText = summaryText;
        this.status = PendingActionStatus.PENDING;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getToolName() {
        return toolName;
    }

    public String getParametersJson() {
        return parametersJson;
    }

    public String getSummaryText() {
        return summaryText;
    }

    public PendingActionStatus getStatus() {
        return status;
    }

    public void setStatus(PendingActionStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Instant confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(Instant executedAt) {
        this.executedAt = executedAt;
    }

    public String getFailureInformation() {
        return failureInformation;
    }

    public void setFailureInformation(String failureInformation) {
        this.failureInformation = failureInformation;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(this.expiresAt);
    }
}
