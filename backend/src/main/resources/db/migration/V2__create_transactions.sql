CREATE TABLE transactions (
    id CHAR(36) NOT NULL PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    merchant VARCHAR(120) NOT NULL,
    category VARCHAR(32) NOT NULL,
    transaction_type VARCHAR(16) NOT NULL,
    transaction_date DATE NOT NULL,
    notes VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_transactions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_transactions_category CHECK (category IN ('FOOD', 'TRAVEL', 'SHOPPING', 'ENTERTAINMENT', 'BILLS', 'HEALTH', 'EDUCATION', 'PERSONAL', 'OTHER')),
    CONSTRAINT chk_transactions_type CHECK (transaction_type IN ('EXPENSE', 'INCOME')),
    INDEX idx_transactions_user_date (user_id, transaction_date DESC),
    INDEX idx_transactions_user_category (user_id, category),
    INDEX idx_transactions_user_type (user_id, transaction_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
