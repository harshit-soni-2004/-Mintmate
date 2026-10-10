package com.spendsense.assistant.proactive;

import com.spendsense.analytics.InsightSeverity;
import com.spendsense.user.User;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assistant_proactive_insights")
public class ProactiveInsightEntity {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 36)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48)
    private ProactiveInsightType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private InsightSeverity severity;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, length = 1000)
    private String summary;

    @Column(name = "supporting_facts_json", nullable = false, columnDefinition = "TEXT")
    private String supportingFactsJson;

    @Column(name = "action_recommendation", length = 255)
    private String actionRecommendation;

    @Column(name = "deduplication_key", nullable = false, length = 128)
    private String deduplicationKey;

    @Column(nullable = false)
    private boolean dismissed;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected ProactiveInsightEntity() {}

    public ProactiveInsightEntity(
            User user,
            ProactiveInsightType type,
            InsightSeverity severity,
            String title,
            String summary,
            String supportingFactsJson,
            String actionRecommendation,
            String deduplicationKey,
            Instant expiresAt
    ) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.type = type;
        this.severity = severity;
        this.title = title;
        this.summary = summary;
        this.supportingFactsJson = supportingFactsJson;
        this.actionRecommendation = actionRecommendation;
        this.deduplicationKey = deduplicationKey;
        this.dismissed = false;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public ProactiveInsightType getType() {
        return type;
    }

    public InsightSeverity getSeverity() {
        return severity;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getSupportingFactsJson() {
        return supportingFactsJson;
    }

    public String getActionRecommendation() {
        return actionRecommendation;
    }

    public String getDeduplicationKey() {
        return deduplicationKey;
    }

    public boolean isDismissed() {
        return dismissed;
    }

    public void setDismissed(boolean dismissed) {
        this.dismissed = dismissed;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
