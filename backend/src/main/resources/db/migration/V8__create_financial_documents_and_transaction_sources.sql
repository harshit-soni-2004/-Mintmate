-- Flyway migration: V8__create_financial_documents_and_transaction_sources.sql
-- Creates financial_documents table and adds source tracking to transactions table

CREATE TABLE financial_documents (
    id CHAR(36) NOT NULL PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    file_type VARCHAR(16) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    storage_key VARCHAR(500) NOT NULL,
    content_hash CHAR(64) NOT NULL,
    institution_name VARCHAR(100) NULL,
    account_type VARCHAR(32) NOT NULL,
    account_number_last4 VARCHAR(4) NULL,
    statement_start_date DATE NULL,
    statement_end_date DATE NULL,
    status VARCHAR(32) NOT NULL,
    total_rows_detected INT NOT NULL DEFAULT 0,
    imported_count INT NOT NULL DEFAULT 0,
    duplicate_count INT NOT NULL DEFAULT 0,
    error_message VARCHAR(2000) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    CONSTRAINT fk_financial_documents_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_financial_doc_type CHECK (file_type IN ('PDF', 'CSV', 'XLSX')),
    CONSTRAINT chk_financial_doc_account_type CHECK (account_type IN ('SAVINGS', 'CURRENT', 'CREDIT_CARD')),
    CONSTRAINT chk_financial_doc_status CHECK (status IN ('UPLOADED', 'PARSING', 'COMPLETED', 'PARTIALLY_PROCESSED', 'FAILED')),
    INDEX idx_financial_docs_user_status (user_id, status),
    INDEX idx_financial_docs_user_hash (user_id, content_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE transactions
    ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL' AFTER categorization_alternatives,
    ADD COLUMN source_document_id CHAR(36) NULL AFTER source_type,
    ADD COLUMN source_reference VARCHAR(120) NULL AFTER source_document_id;

ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_source_document
    FOREIGN KEY (source_document_id) REFERENCES financial_documents (id) ON DELETE SET NULL,
    ADD CONSTRAINT chk_transactions_source_type
    CHECK (source_type IN ('MANUAL', 'STATEMENT_IMPORT', 'SMS_IMPORT', 'API_IMPORT'));

CREATE INDEX idx_transactions_source_doc ON transactions (source_document_id);
