-- CashFlow Server init tables
-- Aligned with Android Room data model

CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `username` VARCHAR(50) NOT NULL UNIQUE,
    `email` VARCHAR(100) NOT NULL UNIQUE,
    `password_hash` VARCHAR(255) NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_user_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `transaction` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `client_id` VARCHAR(36) NOT NULL UNIQUE,
    `user_id` BIGINT NOT NULL,
    `review_state` VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED',
    `ingestion_state` VARCHAR(20) NOT NULL DEFAULT 'RAW',
    `transaction_type` VARCHAR(20) NOT NULL DEFAULT 'EXPENSE',
    `source_type` VARCHAR(30) DEFAULT 'MANUAL',
    `source_fingerprint` VARCHAR(255),
    `source_package` VARCHAR(100) DEFAULT '',
    `source_app_name` VARCHAR(100) DEFAULT '',
    `occurred_at` BIGINT NOT NULL,
    `amount_cents` BIGINT NOT NULL,
    `currency` VARCHAR(10) NOT NULL DEFAULT 'CNY',
    `merchant` VARCHAR(200),
    `category_code` VARCHAR(50),
    `category_name` VARCHAR(50),
    `account_code` VARCHAR(50),
    `account_name` VARCHAR(50),
    `note` TEXT,
    `raw_text` TEXT,
    `image_uri` TEXT,
    `confidence` FLOAT DEFAULT 0,
    `needs_review` BOOLEAN NOT NULL DEFAULT FALSE,
    `created_at` BIGINT NOT NULL,
    `updated_at` BIGINT NOT NULL,
    INDEX `idx_tx_user_id` (`user_id`),
    INDEX `idx_tx_occurred_at` (`occurred_at`),
    INDEX `idx_tx_client_id` (`client_id`),
    INDEX `idx_tx_source_fingerprint` (`source_fingerprint`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `category` (
    `code` VARCHAR(50) PRIMARY KEY,
    `name` VARCHAR(50) NOT NULL,
    `icon_key` VARCHAR(50),
    `color_hex` VARCHAR(7),
    `is_income` BOOLEAN NOT NULL DEFAULT FALSE,
    `monthly_budget_cents` BIGINT DEFAULT 0,
    `is_system` BOOLEAN NOT NULL DEFAULT TRUE,
    INDEX `idx_cat_is_income` (`is_income`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `chat_session` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` BIGINT NOT NULL,
    `title` VARCHAR(100),
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_cs_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `chat_message` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `session_id` BIGINT NOT NULL,
    `role` VARCHAR(20) NOT NULL,
    `content` TEXT,
    `tool_calls` JSON,
    `token_count` INT DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_cm_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
