CREATE TABLE `user` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT,
  `guid` VARCHAR(36) NOT NULL,
  `email` VARCHAR(255) NOT NULL,
  `first_name` VARCHAR(100) NULL,
  `last_name` VARCHAR(100) NULL,
  `user_type` ENUM('STUDENT','VENDOR','COMMUNITY_LEADER','FACULTY') NULL,
  `status` ENUM('PENDING','ACTIVE','SUSPENDED','DEACTIVATED') NOT NULL,
  `verification_status` ENUM('UNVERIFIED','PENDING_REVIEW','VERIFIED','REJECTED') NOT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NULL,
  `version` INT(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_guid` (`guid`),
  UNIQUE KEY `uk_user_email` (`email`),
  KEY `idx_user_verification_status` (`verification_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `user_role` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT,
  `user_guid` VARCHAR(36) NOT NULL,
  `role` ENUM('STUDENT','VENDOR','COMMUNITY_LEADER','FACULTY','ADMIN') NOT NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role_user_guid_role` (`user_guid`, `role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `user_banking_detail` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT,
  `user_guid` VARCHAR(36) NOT NULL,
  `account_holder_name` VARCHAR(150) NULL,
  `bank_name` VARCHAR(100) NULL,
  `account_number_encrypted` VARCHAR(255) NULL,
  `branch_code` VARCHAR(20) NULL,
  `account_type` ENUM('CHEQUE','SAVINGS') NULL,
  `verified` BIT(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_banking_detail_user_guid` (`user_guid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `user_payout_summary` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT,
  `user_guid` VARCHAR(36) NOT NULL,
  `total_sales_count` INT(11) NOT NULL DEFAULT 0,
  `total_sales_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  `total_paid_out_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  `total_pending_payout_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  `last_payout_at` DATETIME NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_payout_summary_user_guid` (`user_guid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `user_session_activity` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT,
  `user_guid` VARCHAR(36) NOT NULL,
  `session_jti` VARCHAR(64) NOT NULL,
  `login_at` DATETIME NOT NULL,
  `logout_at` DATETIME NULL,
  `ip_address` VARCHAR(45) NULL,
  `user_agent` VARCHAR(255) NULL,
  `last_activity_at` DATETIME NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user_session_activity_user_guid_jti` (`user_guid`, `session_jti`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
