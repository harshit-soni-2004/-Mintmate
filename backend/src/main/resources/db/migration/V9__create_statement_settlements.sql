-- Flyway migration: V9__create_statement_settlements.sql
-- Tracks statement settlements, internal transfers, and refunds without corrupting ordinary transactions

CREATE TABLE statement_settlements (
    id CHAR(36) NOT NULL PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    document_id CHAR(36) NOT NULL,
    row_index INT NOT NULL,
    transaction_date DATE NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    raw_merchant VARCHAR(255) NULL,
    raw_description VARCHAR(500) NULL,
    classification VARCHAR(32) NOT NULL,
    account_type VARCHAR(32) NOT NULL,
    external_reference VARCHAR(120) NULL,
    status VARCHAR(32) NOT NULL,
    matched_settlement_id CHAR(36) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    CONSTRAINT fk_settlements_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_settlements_doc FOREIGN KEY (document_id) REFERENCES financial_documents (id) ON DELETE CASCADE,
    CONSTRAINT chk_settlements_status CHECK (status IN ('UNRESOLVED', 'RESOLVED', 'DISCARDED')),
    CONSTRAINT chk_settlements_classification CHECK (classification IN ('PAYMENT', 'TRANSFER', 'REFUND', 'REVERSAL', 'UNKNOWN')),
    INDEX idx_settlements_user_status (user_id, status),
    INDEX idx_settlements_match (user_id, status, amount, transaction_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
