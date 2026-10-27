-- V18: Create email_otps table for email verification codes
CREATE TABLE IF NOT EXISTS email_otps (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    code_hash VARCHAR(128) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 5,
    consumed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_email_otps_lookup (email, purpose, consumed_at),
    INDEX idx_email_otps_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
