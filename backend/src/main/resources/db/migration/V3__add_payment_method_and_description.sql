-- Flyway migration: V3__add_payment_method_and_description.sql
-- Adds payment_method and description columns to transactions table with full backward compatibility

ALTER TABLE transactions
    ADD COLUMN payment_method VARCHAR(32) NULL AFTER transaction_type,
    ADD COLUMN description VARCHAR(255) NULL AFTER merchant;

ALTER TABLE transactions
    ADD CONSTRAINT chk_transactions_payment_method 
    CHECK (payment_method IS NULL OR payment_method IN ('UPI', 'CREDIT_CARD', 'DEBIT_CARD', 'CASH', 'BANK_TRANSFER', 'OTHER'));

CREATE INDEX idx_transactions_user_payment_method ON transactions (user_id, payment_method);
