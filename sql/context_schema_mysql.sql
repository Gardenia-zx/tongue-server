-- Stateless context system tables.
-- Execute this file in the existing tongue_app database before starting the Java backend
-- when spring.jpa.hibernate.ddl-auto=validate.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `agent_conversation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'conversation id',
  `user_id` BIGINT NOT NULL COMMENT 'owner user id',
  `thread_id` VARCHAR(128) DEFAULT NULL COMMENT 'stable Python Agent thread id for this conversation',
  `thread_epoch` INT NOT NULL DEFAULT 1 COMMENT 'Agent thread epoch, incremented only by reset flow',
  `title` VARCHAR(128) DEFAULT NULL COMMENT 'conversation title',
  `active_report_id` BIGINT DEFAULT NULL COMMENT 'current report being discussed',
  `last_image_file_id` BIGINT DEFAULT NULL COMMENT 'latest uploaded tongue image file id',
  `summary_id` BIGINT DEFAULT NULL COMMENT 'latest context summary id',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DELETED',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_agent_conversation_user_status_updated` (`user_id`, `status`, `updated_at`),
  KEY `idx_agent_conversation_active_report` (`user_id`, `active_report_id`, `status`),
  KEY `idx_agent_conversation_thread_id` (`thread_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='agent conversation state';

CREATE TABLE IF NOT EXISTS `agent_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'message id',
  `conversation_id` BIGINT NOT NULL COMMENT 'conversation id',
  `user_id` BIGINT NOT NULL COMMENT 'owner user id',
  `external_message_id` VARCHAR(128) DEFAULT NULL COMMENT 'stable message id generated before calling Python Agent',
  `role` VARCHAR(32) NOT NULL COMMENT 'user/assistant/system/tool',
  `content` TEXT COMMENT 'original message content',
  `content_type` VARCHAR(32) NOT NULL DEFAULT 'text' COMMENT 'text/image/mixed/tool_result',
  `image_file_id` BIGINT DEFAULT NULL COMMENT 'related image file id',
  `report_id` BIGINT DEFAULT NULL COMMENT 'related report id',
  `metadata_json` JSON DEFAULT NULL COMMENT 'message metadata',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_message_external` (`conversation_id`, `user_id`, `external_message_id`),
  KEY `idx_agent_message_conversation_created` (`conversation_id`, `user_id`, `created_at`),
  KEY `idx_agent_message_user_report` (`user_id`, `report_id`, `created_at`),
  KEY `idx_agent_message_image_file` (`image_file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='agent original messages';

CREATE TABLE IF NOT EXISTS `agent_context_summary` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'context summary id',
  `conversation_id` BIGINT NOT NULL COMMENT 'conversation id',
  `user_id` BIGINT NOT NULL COMMENT 'owner user id',
  `summary_text` TEXT COMMENT 'compressed context text injected into model context',
  `structured_summary_json` JSON DEFAULT NULL COMMENT 'structured compressed summary',
  `source_start_message_id` BIGINT DEFAULT NULL COMMENT 'first source message id',
  `source_end_message_id` BIGINT DEFAULT NULL COMMENT 'last source message id',
  `source_message_ids_json` JSON DEFAULT NULL COMMENT 'source message ids',
  `source_report_ids_json` JSON DEFAULT NULL COMMENT 'source report ids',
  `source_file_ids_json` JSON DEFAULT NULL COMMENT 'source file ids',
  `source_evidence_ids_json` JSON DEFAULT NULL COMMENT 'source evidence ids',
  `compression_version` VARCHAR(64) DEFAULT 'context-summary-v1' COMMENT 'compression policy version',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DELETED',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_context_summary_conversation_created` (`conversation_id`, `user_id`, `created_at`),
  KEY `idx_context_summary_status` (`conversation_id`, `status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='agent compressed context summaries';

CREATE TABLE IF NOT EXISTS `agent_memory_outbox` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'outbox id',
  `event_id` VARCHAR(128) NOT NULL COMMENT 'stable outbox event id',
  `tenant_id` VARCHAR(128) NOT NULL COMMENT 'tenant id',
  `user_id` BIGINT NOT NULL COMMENT 'owner user id',
  `conversation_id` BIGINT NOT NULL COMMENT 'conversation id',
  `thread_id` VARCHAR(128) NOT NULL COMMENT 'agent thread id',
  `thread_epoch` INT NOT NULL DEFAULT 1 COMMENT 'agent thread epoch',
  `turn_id` VARCHAR(256) NOT NULL COMMENT 'agent turn id',
  `user_message_id` VARCHAR(128) DEFAULT NULL COMMENT 'source user message id',
  `assistant_message_id` VARCHAR(128) DEFAULT NULL COMMENT 'source assistant message id',
  `active_report_id` BIGINT DEFAULT NULL COMMENT 'active report id',
  `event_type` VARCHAR(64) NOT NULL DEFAULT 'AGENT_TURN_COMPLETED' COMMENT 'event type',
  `status` VARCHAR(32) NOT NULL DEFAULT 'NEW' COMMENT 'NEW/PUBLISHED/PUBLISH_FAILED',
  `retry_count` INT NOT NULL DEFAULT 0 COMMENT 'publish retry count',
  `payload_ref_json` JSON DEFAULT NULL COMMENT 'message references and non-sensitive metadata',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_memory_outbox_event` (`event_id`),
  KEY `idx_agent_memory_outbox_status` (`status`, `created_at`),
  KEY `idx_agent_memory_outbox_turn` (`tenant_id`, `turn_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Java-owned memory event outbox';
