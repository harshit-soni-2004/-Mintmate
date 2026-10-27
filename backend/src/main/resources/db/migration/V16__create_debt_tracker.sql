-- Flyway migration: V16__create_debt_tracker.sql
-- Creates table for single-user IOU / debt tracker (receivables, payables, due dates, settlement links)

CREATE TABLE debts (
    id CHAR(36) NOT NULL PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    counterparty_name VARCHAR(100) NOT NULL,
    debt_type VARCHAR(32) NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    due_date DATE NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    settled_at DATETIME(6) NULL,
    linked_transaction_id CHAR(36) NULL,
    notes VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    CONSTRAINT fk_debt_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_debt_tx FOREIGN KEY (linked_transaction_id) REFERENCES transactions (id) ON DELETE SET NULL,
    INDEX idx_debt_user_status (user_id, status),
    INDEX idx_debt_user_due_date (user_id, due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
