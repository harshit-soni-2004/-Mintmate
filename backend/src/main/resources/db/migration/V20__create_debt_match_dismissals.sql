-- Flyway migration: V17__create_debt_match_dismissals.sql
-- Stores user dismissals for candidate debt matches ("Not a match")

CREATE TABLE debt_match_dismissals (
    id CHAR(36) NOT NULL PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    debt_id CHAR(36) NOT NULL,
    transaction_id CHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL,

    CONSTRAINT fk_dmd_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_dmd_debt FOREIGN KEY (debt_id) REFERENCES debts (id) ON DELETE CASCADE,
    CONSTRAINT fk_dmd_tx FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE CASCADE,
    UNIQUE KEY uq_debt_match_dismissal (user_id, debt_id, transaction_id),
    INDEX idx_dmd_user_debt (user_id, debt_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
