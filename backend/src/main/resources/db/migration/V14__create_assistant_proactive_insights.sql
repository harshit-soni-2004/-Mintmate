-- Flyway migration: V14__create_assistant_proactive_insights.sql
-- Creates persistent assistant proactive insights table for Phase 8D Proactive Financial Intelligence.

CREATE TABLE assistant_proactive_insights (
    id CHAR(36) NOT NULL PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    type VARCHAR(48) NOT NULL,
    severity VARCHAR(24) NOT NULL,
    title VARCHAR(255) NOT NULL,
    summary VARCHAR(1000) NOT NULL,
    supporting_facts_json TEXT NOT NULL,
    action_recommendation VARCHAR(255) NULL,
    deduplication_key VARCHAR(128) NOT NULL,
    dismissed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,

    CONSTRAINT fk_proactive_insights_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_proactive_insights_user_dedup (user_id, deduplication_key, created_at),
    INDEX idx_proactive_insights_user_active (user_id, dismissed, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
