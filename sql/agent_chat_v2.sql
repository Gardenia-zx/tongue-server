-- Agent Chat V2 production schema
-- Apply this migration before enabling POST /api/v2/agent/chat in environments
-- where spring.jpa.hibernate.ddl-auto is validate/none.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `agent_conversation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `conversation_id` VARCHAR(128) NOT NULL,
  `user_id` BIGINT NOT NULL,
  `thread_id` VARCHAR(128) NOT NULL,
  `thread_epoch` INT NOT NULL DEFAULT 1,
  `active_report_id` BIGINT DEFAULT NULL,
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_conversation_user_conversation` (`user_id`, `conversation_id`),
  UNIQUE KEY `uk_agent_conversation_user_thread_epoch` (`user_id`, `thread_id`, `thread_epoch`),
  KEY `idx_agent_conversation_user_updated` (`user_id`, `updated_at`),
  KEY `idx_agent_conversation_active_report` (`active_report_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `agent_turn` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `turn_id` VARCHAR(128) NOT NULL,
  `conversation_id` VARCHAR(128) NOT NULL,
  `user_id` BIGINT NOT NULL,
  `request_id` VARCHAR(128) NOT NULL,
  `request_hash` VARCHAR(64) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `input_content` LONGTEXT NOT NULL,
  `context_binding` VARCHAR(32) NOT NULL DEFAULT 'NONE',
  `bound_report_id` BIGINT DEFAULT NULL,
  `response_json` LONGTEXT DEFAULT NULL,
  `trace_id` VARCHAR(128) NOT NULL,
  `error_code` VARCHAR(64) DEFAULT NULL,
  `error_message` LONGTEXT DEFAULT NULL,
  `started_at` DATETIME NOT NULL,
  `finished_at` DATETIME DEFAULT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_turn_user_request` (`user_id`, `request_id`),
  UNIQUE KEY `uk_agent_turn_turn_id` (`turn_id`),
  KEY `idx_agent_turn_conversation_created` (`conversation_id`, `created_at`),
  KEY `idx_agent_turn_user_status` (`user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `agent_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `message_id` VARCHAR(128) NOT NULL,
  `turn_id` VARCHAR(128) NOT NULL,
  `conversation_id` VARCHAR(128) NOT NULL,
  `user_id` BIGINT NOT NULL,
  `role` VARCHAR(16) NOT NULL,
  `content_type` VARCHAR(32) NOT NULL DEFAULT 'text',
  `content` LONGTEXT NOT NULL,
  `structured_content_json` LONGTEXT DEFAULT NULL,
  `report_id` BIGINT DEFAULT NULL,
  `status` VARCHAR(32) NOT NULL,
  `sequence_no` BIGINT NOT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_message_message_id` (`message_id`),
  KEY `idx_agent_message_conversation_sequence` (`conversation_id`, `sequence_no`),
  KEY `idx_agent_message_turn_id` (`turn_id`),
  KEY `idx_agent_message_user_created` (`user_id`, `created_at`),
  CONSTRAINT `chk_agent_message_user_report` CHECK (`role` <> 'USER' OR `report_id` IS NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
