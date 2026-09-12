-- Flyway migration: V5__create_goals.sql
-- Creates goals table for tracking savings milestones and financial ambitions

CREATE TABLE goals (
    id CHAR(36) NOT NULL PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    name VARCHAR(120) NOT NULL,
    target_amount DECIMAL(19, 2) NOT NULL,
    current_amount DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    target_date DATE NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_goals_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_goals_target_positive CHECK (target_amount > 0),
    CONSTRAINT chk_goals_current_nonnegative CHECK (current_amount >= 0),
    INDEX idx_goals_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
