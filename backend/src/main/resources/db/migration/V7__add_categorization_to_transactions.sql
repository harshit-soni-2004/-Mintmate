-- Flyway migration: V7__add_categorization_to_transactions.sql
-- Adds categorization metadata and review tracking to transactions

ALTER TABLE transactions
    ADD COLUMN categorization_confidence DECIMAL(5, 4) NULL AFTER category,
    ADD COLUMN categorization_confidence_level VARCHAR(16) NULL AFTER categorization_confidence,
    ADD COLUMN categorization_source VARCHAR(32) NULL AFTER categorization_confidence_level,
    ADD COLUMN requires_review BOOLEAN NOT NULL DEFAULT FALSE AFTER categorization_source,
    ADD COLUMN predicted_category VARCHAR(32) NULL AFTER requires_review,
    ADD COLUMN categorization_alternatives VARCHAR(1000) NULL AFTER predicted_category;

ALTER TABLE transactions
    ADD CONSTRAINT chk_transactions_confidence_level
    CHECK (categorization_confidence_level IS NULL OR categorization_confidence_level IN ('HIGH', 'MEDIUM', 'LOW', 'NONE')),
    ADD CONSTRAINT chk_transactions_cat_source
    CHECK (categorization_source IS NULL OR categorization_source IN ('RULE', 'USER_MEMORY', 'ML', 'FALLBACK', 'MANUAL')),
    ADD CONSTRAINT chk_transactions_predicted_category
    CHECK (predicted_category IS NULL OR predicted_category IN ('FOOD', 'TRAVEL', 'SHOPPING', 'ENTERTAINMENT', 'BILLS', 'HEALTH', 'EDUCATION', 'PERSONAL', 'OTHER'));

CREATE INDEX idx_transactions_user_review ON transactions (user_id, requires_review);
