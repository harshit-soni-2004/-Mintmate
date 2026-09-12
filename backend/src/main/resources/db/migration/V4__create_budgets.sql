-- Flyway migration: V4__create_budgets.sql
-- Creates budgets table for user-scoped category spending limits

CREATE TABLE budgets (
    id CHAR(36) NOT NULL PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    category VARCHAR(32) NOT NULL,
    limit_amount DECIMAL(19, 2) NOT NULL,
    period VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_budgets_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_budgets_limit_positive CHECK (limit_amount > 0),
    CONSTRAINT chk_budgets_dates CHECK (end_date >= start_date),
    CONSTRAINT chk_budgets_period CHECK (period IN ('MONTHLY', 'WEEKLY', 'YEARLY', 'CUSTOM')),
    CONSTRAINT chk_budgets_category CHECK (category IN ('FOOD', 'TRAVEL', 'SHOPPING', 'ENTERTAINMENT', 'BILLS', 'HEALTH', 'EDUCATION', 'PERSONAL', 'OTHER')),
    CONSTRAINT uk_budgets_user_cat_period_date UNIQUE (user_id, category, period, start_date),
    INDEX idx_budgets_user_dates (user_id, start_date, end_date),
    INDEX idx_budgets_user_category (user_id, category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
