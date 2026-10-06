CREATE TABLE `auth_credential` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT,
  `user_guid` VARCHAR(36) NOT NULL,
  `password_hash` VARCHAR(255) NOT NULL,
  `last_login_at` DATETIME NULL,
  `failed_login_attempts` INT(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auth_credential_user_guid` (`user_guid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `auth_verification_token` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT,
  `user_guid` VARCHAR(36) NOT NULL,
  `token` VARCHAR(64) NOT NULL,
  `token_type` ENUM('EMAIL_VERIFY','VENDOR_BUSINESS_VERIFY') NOT NULL,
  `expires_at` DATETIME NOT NULL,
  `consumed_at` DATETIME NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auth_verification_token_token` (`token`),
  KEY `idx_auth_verification_token_user_guid` (`user_guid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
