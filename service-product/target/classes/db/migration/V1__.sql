CREATE TABLE `product` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT,
  `guid` VARCHAR(36) NOT NULL,
  `vendor_user_guid` VARCHAR(36) NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `description` TEXT NULL,
  `price` DECIMAL(10,2) NOT NULL,
  `condition_type` ENUM('NEW','SECOND_HAND') NOT NULL,
  `category` VARCHAR(100) NULL,
  `stock_quantity` INT(11) NOT NULL DEFAULT 0,
  `status` ENUM('DRAFT','ACTIVE','SOLD_OUT','REMOVED') NOT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_guid` (`guid`),
  KEY `idx_product_vendor_user_guid` (`vendor_user_guid`),
  KEY `idx_product_status_title` (`status`, `title`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `product_image` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT,
  `product_guid` VARCHAR(36) NOT NULL,
  `url` VARCHAR(500) NOT NULL,
  `sort_order` INT(11) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_product_image_product_guid` (`product_guid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `community_activity` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT,
  `guid` VARCHAR(36) NOT NULL,
  `organiser_user_guid` VARCHAR(36) NOT NULL,
  `title` VARCHAR(255) NOT NULL,
  `description` TEXT NULL,
  `activity_date` DATETIME NOT NULL,
  `location` VARCHAR(255) NULL,
  `status` ENUM('DRAFT','PUBLISHED','CANCELLED','COMPLETED') NOT NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_community_activity_guid` (`guid`),
  KEY `idx_community_activity_status_date` (`status`, `activity_date`),
  KEY `idx_community_activity_organiser` (`organiser_user_guid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `order_transaction` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT,
  `guid` VARCHAR(36) NOT NULL,
  `buyer_user_guid` VARCHAR(36) NOT NULL,
  `vendor_user_guid` VARCHAR(36) NOT NULL,
  `product_guid` VARCHAR(36) NOT NULL,
  `quantity` INT(11) NOT NULL,
  `unit_price` DECIMAL(10,2) NOT NULL,
  `total_amount` DECIMAL(12,2) NOT NULL,
  `escrow_status` ENUM('CREATED','AWAITING_PAYMENT','ESCROW_HELD','SHIPPED','DELIVERED_CONFIRMED','FUNDS_RELEASED','DISPUTED','CANCELLED','REFUNDED') NOT NULL,
  `escrow_reference_number` VARCHAR(32) NULL,
  `paid_at` DATETIME NULL,
  `shipped_at` DATETIME NULL,
  `delivery_confirmed_at` DATETIME NULL,
  `funds_released_at` DATETIME NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_transaction_guid` (`guid`),
  KEY `idx_order_transaction_buyer` (`buyer_user_guid`),
  KEY `idx_order_transaction_vendor` (`vendor_user_guid`),
  KEY `idx_order_transaction_status` (`escrow_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `product_review` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT,
  `product_guid` VARCHAR(36) NOT NULL,
  `reviewer_user_guid` VARCHAR(36) NOT NULL,
  `order_transaction_guid` VARCHAR(36) NOT NULL,
  `rating` TINYINT(1) NOT NULL,
  `comment` TEXT NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_review_order` (`order_transaction_guid`),
  KEY `idx_product_review_product_guid` (`product_guid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `activity_review` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT,
  `community_activity_guid` VARCHAR(36) NOT NULL,
  `reviewer_user_guid` VARCHAR(36) NOT NULL,
  `rating` TINYINT(1) NOT NULL,
  `comment` TEXT NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity_review_activity_reviewer` (`community_activity_guid`, `reviewer_user_guid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
