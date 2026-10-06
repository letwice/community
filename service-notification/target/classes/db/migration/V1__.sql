CREATE TABLE `notification` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT,
  `user_guid` VARCHAR(36) NOT NULL,
  `type` ENUM('EMAIL','SMS','RICH_INBOX') NOT NULL,
  `subject` VARCHAR(255) NOT NULL,
  `body` TEXT NULL,
  `read_flag` BIT(1) NOT NULL DEFAULT b'0',
  `related_entity_type` ENUM('ORDER','PRODUCT','ACTIVITY','ACCOUNT') NULL,
  `related_entity_id` VARCHAR(36) NULL,
  `dispatch_status` ENUM('SIMULATED_SENT','FAILED','DELIVERED_INBOX') NOT NULL,
  `created_at` DATETIME NOT NULL,
  `read_at` DATETIME NULL,
  PRIMARY KEY (`id`),
  KEY `idx_notification_user_guid_type_read` (`user_guid`, `type`, `read_flag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
