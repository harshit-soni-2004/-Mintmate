-- Flyway migration: V6__create_categorization_memory_and_feedback.sql
-- Creates tables for user-specific categorization memory and audit/feedback for offline retraining

CREATE TABLE user_categorization_memories (
    id CHAR(36) NOT NULL PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    normalized_merchant VARCHAR(120) NOT NULL,
    category VARCHAR(32) NOT NULL,
    usage_count INT NOT NULL DEFAULT 1,
    last_used_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_user_cat_memories_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_user_cat_memory_category CHECK (category IN ('FOOD', 'TRAVEL', 'SHOPPING', 'ENTERTAINMENT', 'BILLS', 'HEALTH', 'EDUCATION', 'PERSONAL', 'OTHER')),
    CONSTRAINT chk_user_cat_memory_usage_count CHECK (usage_count > 0),
    CONSTRAINT uk_user_cat_memory_user_merchant_cat UNIQUE (user_id, normalized_merchant, category),
    INDEX idx_user_cat_memory_lookup (user_id, normalized_merchant)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE categorization_feedbacks (
    id CHAR(36) NOT NULL PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    transaction_id CHAR(36) NULL,
    raw_merchant VARCHAR(120) NULL,
    normalized_merchant VARCHAR(120) NOT NULL,
    raw_description VARCHAR(255) NULL,
    amount DECIMAL(19, 2) NULL,
    predicted_category VARCHAR(32) NULL,
    confidence DECIMAL(5, 4) NULL,
    confidence_level VARCHAR(16) NULL,
    final_category VARCHAR(32) NOT NULL,
    is_corrected BOOLEAN NOT NULL DEFAULT FALSE,
    prediction_source VARCHAR(32) NOT NULL,
    model_version VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_cat_feedback_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_cat_feedback_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE SET NULL,
    CONSTRAINT chk_cat_feedback_predicted_cat CHECK (predicted_category IS NULL OR predicted_category IN ('FOOD', 'TRAVEL', 'SHOPPING', 'ENTERTAINMENT', 'BILLS', 'HEALTH', 'EDUCATION', 'PERSONAL', 'OTHER')),
    CONSTRAINT chk_cat_feedback_final_cat CHECK (final_category IN ('FOOD', 'TRAVEL', 'SHOPPING', 'ENTERTAINMENT', 'BILLS', 'HEALTH', 'EDUCATION', 'PERSONAL', 'OTHER')),
    CONSTRAINT chk_cat_feedback_confidence_level CHECK (confidence_level IS NULL OR confidence_level IN ('HIGH', 'MEDIUM', 'LOW', 'NONE')),
    CONSTRAINT chk_cat_feedback_prediction_source CHECK (prediction_source IN ('RULE', 'USER_MEMORY', 'ML', 'FALLBACK', 'MANUAL')),
    INDEX idx_cat_feedback_user_created (user_id, created_at DESC),
    INDEX idx_cat_feedback_created (created_at DESC),
    INDEX idx_cat_feedback_merchant (normalized_merchant)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
