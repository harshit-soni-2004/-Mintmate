-- Flyway migration: V13__create_assistant_pending_actions.sql
-- Creates persistent assistant pending actions table for Phase 8C safe financial mutations.

CREATE TABLE assistant_pending_actions (
    id CHAR(36) NOT NULL PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    tool_name VARCHAR(64) NOT NULL,
    parameters_json TEXT NOT NULL,
    summary_text VARCHAR(500) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    confirmed_at DATETIME(6) NULL,
    executed_at DATETIME(6) NULL,
    failure_information VARCHAR(1000) NULL,

    CONSTRAINT fk_pending_actions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT chk_pending_action_status CHECK (status IN ('PENDING', 'CONFIRMED', 'EXECUTED', 'EXPIRED', 'CANCELLED', 'FAILED')),
    INDEX idx_pending_actions_user_status (user_id, status),
    INDEX idx_pending_actions_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
