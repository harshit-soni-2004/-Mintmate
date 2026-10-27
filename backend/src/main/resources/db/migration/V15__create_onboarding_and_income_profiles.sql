-- Flyway migration: V15__create_onboarding_and_income_profiles.sql
-- Creates financial profiles, declared income sources, and baseline category allocations for onboarding & income classification.

CREATE TABLE user_financial_profiles (
    id CHAR(36) NOT NULL PRIMARY KEY,
    user_id CHAR(36) NOT NULL UNIQUE,
    persona VARCHAR(32) NOT NULL,
    monthly_savings_target DECIMAL(12, 2) NULL,
    monthly_investment_target DECIMAL(12, 2) NULL,
    onboarding_completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    CONSTRAINT fk_ufp_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_ufp_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_income_sources (
    id CHAR(36) NOT NULL PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    source_name VARCHAR(100) NOT NULL,
    income_type VARCHAR(32) NOT NULL,
    frequency VARCHAR(32) NOT NULL,
    declared_amount DECIMAL(12, 2) NOT NULL,
    expected_day_of_month INT NULL,
    employer_or_payer_keyword VARCHAR(100) NULL,
    is_primary BOOLEAN NOT NULL DEFAULT TRUE,
    is_variable BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    CONSTRAINT fk_uis_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_uis_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE baseline_category_allocations (
    id CHAR(36) NOT NULL PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    category VARCHAR(50) NOT NULL,
    estimated_amount DECIMAL(12, 2) NOT NULL,
    created_at DATETIME(6) NOT NULL,

    CONSTRAINT fk_bca_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_bca_user_cat UNIQUE (user_id, category),
    INDEX idx_bca_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE transactions
    ADD COLUMN income_tier VARCHAR(32) NULL,
    ADD COLUMN is_income_verified BOOLEAN NOT NULL DEFAULT FALSE;
