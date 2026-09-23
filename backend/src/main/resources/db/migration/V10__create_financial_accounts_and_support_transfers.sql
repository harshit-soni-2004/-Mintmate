-- Flyway migration: V10__create_financial_accounts_and_support_transfers.sql
-- Introduces first-class FinancialAccount domain, supports TRANSFER, INVESTMENT, and CASH_WITHDRAWAL transaction types,
-- and enables paired-ledger internal transfer matching.

-- 1. Create financial_accounts table
CREATE TABLE financial_accounts (
    id CHAR(36) NOT NULL PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    name VARCHAR(100) NOT NULL,
    institution_name VARCHAR(100) NOT NULL,
    account_type VARCHAR(32) NOT NULL,
    account_number_last4 VARCHAR(4) NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    CONSTRAINT fk_financial_accounts_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_account_type CHECK (account_type IN ('SAVINGS', 'CURRENT', 'CREDIT_CARD', 'INVESTMENT', 'CASH_WALLET')),
    INDEX idx_financial_accounts_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Link financial_documents to financial_accounts
ALTER TABLE financial_documents
    ADD COLUMN account_id CHAR(36) NULL AFTER user_id;

ALTER TABLE financial_documents
    ADD CONSTRAINT fk_financial_docs_account
    FOREIGN KEY (account_id) REFERENCES financial_accounts (id) ON DELETE SET NULL;

CREATE INDEX idx_financial_docs_account ON financial_documents (account_id);

-- 3. Modify transactions table to support TRANSFER, INVESTMENT, CASH_WITHDRAWAL and transfer tracking
ALTER TABLE transactions
    DROP CHECK chk_transactions_type;

ALTER TABLE transactions
    ADD CONSTRAINT chk_transactions_type
    CHECK (transaction_type IN ('EXPENSE', 'INCOME', 'TRANSFER', 'INVESTMENT', 'CASH_WITHDRAWAL'));

ALTER TABLE transactions
    ADD COLUMN account_id CHAR(36) NULL AFTER user_id,
    ADD COLUMN transfer_direction VARCHAR(16) NULL AFTER transaction_type,
    ADD COLUMN counterparty_account_id CHAR(36) NULL AFTER account_id,
    ADD COLUMN linked_transaction_id CHAR(36) NULL AFTER source_reference,
    ADD COLUMN transfer_match_status VARCHAR(32) NOT NULL DEFAULT 'NONE' AFTER transfer_direction;

ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_account
    FOREIGN KEY (account_id) REFERENCES financial_accounts (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_transactions_counterparty_account
    FOREIGN KEY (counterparty_account_id) REFERENCES financial_accounts (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_transactions_linked_tx
    FOREIGN KEY (linked_transaction_id) REFERENCES transactions (id) ON DELETE SET NULL,
    ADD CONSTRAINT chk_transfer_direction
    CHECK (transfer_direction IS NULL OR transfer_direction IN ('IN', 'OUT')),
    ADD CONSTRAINT chk_transfer_match_status
    CHECK (transfer_match_status IN ('NONE', 'UNMATCHED', 'AUTO_MATCHED', 'MANUALLY_CONFIRMED'));

CREATE INDEX idx_transactions_account ON transactions (account_id);
CREATE INDEX idx_transactions_transfer ON transactions (user_id, transaction_type, transfer_match_status);

-- 4. Conservative Data Migration
-- A. Create distinct accounts derived from existing uploaded documents
INSERT INTO financial_accounts (id, user_id, name, institution_name, account_type, account_number_last4, currency, is_active, is_primary, created_at, updated_at)
SELECT 
    UUID(),
    d.user_id,
    CONCAT(COALESCE(d.institution_name, 'Account'), ' (', d.account_type, IF(d.account_number_last4 IS NOT NULL, CONCAT(' - ', d.account_number_last4), ''), ')'),
    COALESCE(d.institution_name, 'Bank'),
    d.account_type,
    d.account_number_last4,
    'INR',
    TRUE,
    FALSE,
    NOW(6),
    NOW(6)
FROM (SELECT DISTINCT user_id, institution_name, account_type, account_number_last4 FROM financial_documents) d;

-- B. Backfill account_id in financial_documents
UPDATE financial_documents doc
JOIN financial_accounts acc ON doc.user_id = acc.user_id 
    AND COALESCE(doc.institution_name, 'Bank') = acc.institution_name 
    AND doc.account_type = acc.account_type
    AND (doc.account_number_last4 = acc.account_number_last4 OR (doc.account_number_last4 IS NULL AND acc.account_number_last4 IS NULL))
SET doc.account_id = acc.id;

-- C. Backfill account_id in statement-imported transactions
UPDATE transactions t
JOIN financial_documents doc ON t.source_document_id = doc.id
SET t.account_id = doc.account_id
WHERE t.source_document_id IS NOT NULL;

-- Manual transactions (source_document_id IS NULL) are intentionally left with account_id = NULL
