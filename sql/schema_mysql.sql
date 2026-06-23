-- 舌诊 App 生产版后端 MySQL 建表脚本
-- 适用：MySQL 8.x / utf8mb4
-- 使用方式：
--   1. 先在 DataGrip 中创建数据库，例如 tongue_app
--   2. 选中 tongue_app 数据库
--   3. 执行本文件
--
-- 注意：
--   - 本脚本不包含 DROP TABLE，避免误删数据。
--   - 字段命名使用 snake_case，对齐 Spring Boot / Hibernate 默认命名策略。
--   - JSON 字段要求 MySQL 5.7+，推荐 MySQL 8.x。

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `app_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `phone` VARCHAR(32) NOT NULL COMMENT '手机号',
  `role` VARCHAR(32) NOT NULL DEFAULT 'USER' COMMENT '角色：USER/DOCTOR/ADMIN',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DELETED/DISABLED',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_user_phone` (`phone`),
  KEY `idx_app_user_role` (`role`),
  KEY `idx_app_user_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='App用户表';

CREATE TABLE IF NOT EXISTS `user_profile` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '资料ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `nickname` VARCHAR(64) DEFAULT NULL COMMENT '昵称',
  `gender` VARCHAR(16) DEFAULT NULL COMMENT '性别',
  `age` INT DEFAULT NULL COMMENT '年龄',
  `avatar_file_id` BIGINT DEFAULT NULL COMMENT '头像文件ID',
  `health_focus` TEXT COMMENT '健康关注方向',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_profile_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户资料表';

CREATE TABLE IF NOT EXISTS `doctor_profile` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '医生资料ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `real_name` VARCHAR(64) DEFAULT NULL COMMENT '医生姓名',
  `title` VARCHAR(64) DEFAULT NULL COMMENT '职称',
  `introduction` TEXT COMMENT '简介',
  `specialty` TEXT COMMENT '擅长方向',
  `review_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '审核状态：PENDING/APPROVED/REJECTED',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_doctor_profile_user_id` (`user_id`),
  KEY `idx_doctor_profile_review_status` (`review_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='医生资料表';

CREATE TABLE IF NOT EXISTS `sms_code` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '验证码ID',
  `phone` VARCHAR(32) NOT NULL COMMENT '手机号',
  `code` VARCHAR(16) NOT NULL COMMENT '验证码',
  `scene` VARCHAR(32) NOT NULL DEFAULT 'LOGIN' COMMENT '场景',
  `expires_at` DATETIME NOT NULL COMMENT '过期时间',
  `used_at` DATETIME DEFAULT NULL COMMENT '使用时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_sms_code_phone_scene_created` (`phone`, `scene`, `created_at`),
  KEY `idx_sms_code_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='短信验证码表';

CREATE TABLE IF NOT EXISTS `user_token` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Token记录ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `token_hash` VARCHAR(128) NOT NULL COMMENT 'Token哈希',
  `expires_at` DATETIME NOT NULL COMMENT '过期时间',
  `revoked_at` DATETIME DEFAULT NULL COMMENT '撤销时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_token_hash` (`token_hash`),
  KEY `idx_user_token_user_id` (`user_id`),
  KEY `idx_user_token_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户登录Token表';

CREATE TABLE IF NOT EXISTS `admin_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '管理员ID',
  `username` VARCHAR(64) NOT NULL COMMENT '用户名',
  `password_hash` VARCHAR(128) NOT NULL COMMENT '密码哈希',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_user_username` (`username`),
  KEY `idx_admin_user_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员账号表';

CREATE TABLE IF NOT EXISTS `file_object` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '文件ID',
  `owner_user_id` BIGINT NOT NULL COMMENT '归属用户ID',
  `storage_mode` VARCHAR(32) NOT NULL DEFAULT 'local' COMMENT '存储模式：local/minio/oss',
  `bucket` VARCHAR(128) DEFAULT NULL COMMENT '存储桶',
  `object_key` VARCHAR(512) NOT NULL COMMENT '对象Key',
  `storage_path` VARCHAR(512) DEFAULT NULL COMMENT '本地存储路径',
  `public_url` VARCHAR(512) DEFAULT NULL COMMENT '可访问URL',
  `original_filename` VARCHAR(255) DEFAULT NULL COMMENT '原始文件名',
  `content_type` VARCHAR(128) DEFAULT NULL COMMENT '文件类型',
  `file_size` BIGINT DEFAULT NULL COMMENT '文件大小',
  `checksum` VARCHAR(128) DEFAULT NULL COMMENT '文件校验值',
  `purpose` VARCHAR(64) DEFAULT NULL COMMENT '用途',
  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DELETED',
  `deleted_at` DATETIME DEFAULT NULL COMMENT '删除时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_file_object_owner_status_created` (`owner_user_id`, `status`, `created_at`),
  KEY `idx_file_object_purpose` (`purpose`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件对象表';

CREATE TABLE IF NOT EXISTS `tongue_image_file` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '舌象图片记录ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `file_object_id` BIGINT NOT NULL COMMENT '文件对象ID',
  `report_id` BIGINT DEFAULT NULL COMMENT '报告ID',
  `purpose` VARCHAR(64) DEFAULT 'tongue_image' COMMENT '用途',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tongue_image_file_user_id` (`user_id`),
  KEY `idx_tongue_image_file_report_id` (`report_id`),
  KEY `idx_tongue_image_file_file_object_id` (`file_object_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='舌象图片文件表';

CREATE TABLE IF NOT EXISTS `tongue_analysis_task` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `report_id` BIGINT NOT NULL COMMENT '报告ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态',
  `current_stage` VARCHAR(64) DEFAULT 'PENDING' COMMENT '当前阶段',
  `progress` DOUBLE DEFAULT 0 COMMENT '进度',
  `request_id` VARCHAR(128) DEFAULT NULL COMMENT 'Agent请求ID',
  `trace_id` VARCHAR(128) DEFAULT NULL COMMENT '链路追踪ID',
  `error_code` VARCHAR(64) DEFAULT NULL COMMENT '错误码',
  `error_message` TEXT COMMENT '错误信息',
  `started_at` DATETIME DEFAULT NULL COMMENT '开始时间',
  `finished_at` DATETIME DEFAULT NULL COMMENT '完成时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tongue_task_user_created` (`user_id`, `created_at`),
  KEY `idx_tongue_task_report_id` (`report_id`),
  KEY `idx_tongue_task_status` (`status`),
  KEY `idx_tongue_task_trace_id` (`trace_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='舌象分析任务表';

CREATE TABLE IF NOT EXISTS `tongue_report` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '报告ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `task_id` BIGINT DEFAULT NULL COMMENT '任务ID',
  `image_file_id` BIGINT DEFAULT NULL COMMENT '图片文件ID',
  `thread_id` VARCHAR(128) DEFAULT NULL COMMENT 'Agent会话ID',
  `report_status` VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '报告状态：DRAFT/FINAL',
  `source_type` VARCHAR(32) DEFAULT 'AI' COMMENT '来源：AI/DOCTOR',
  `summary` TEXT COMMENT '报告摘要',
  `feature_summary` TEXT COMMENT '特征摘要',
  `detected_feature_codes` JSON DEFAULT NULL COMMENT '识别特征编码',
  `standard_features_json` JSON DEFAULT NULL COMMENT '标准特征JSON',
  `rag_query` TEXT COMMENT 'RAG检索query',
  `rag_grounded` TINYINT(1) DEFAULT 0 COMMENT '是否有知识库依据',
  `rag_evidence_json` JSON DEFAULT NULL COMMENT 'RAG依据JSON',
  `draft_report_json` JSON DEFAULT NULL COMMENT 'Agent完整报告JSON',
  `risk_disclaimer` TEXT COMMENT '风险提示',
  `deleted_at` DATETIME DEFAULT NULL COMMENT '删除时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tongue_report_user_created` (`user_id`, `created_at`),
  KEY `idx_tongue_report_task_id` (`task_id`),
  KEY `idx_tongue_report_status` (`report_status`),
  KEY `idx_tongue_report_deleted_at` (`deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='舌象报告表';

CREATE TABLE IF NOT EXISTS `tongue_report_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '报告版本ID',
  `report_id` BIGINT NOT NULL COMMENT '报告ID',
  `version_no` INT NOT NULL COMMENT '版本号',
  `source_type` VARCHAR(32) DEFAULT 'AI' COMMENT '来源：AI/DOCTOR',
  `summary` TEXT COMMENT '版本摘要',
  `report_json` JSON DEFAULT NULL COMMENT '报告版本JSON',
  `doctor_review_id` BIGINT DEFAULT NULL COMMENT '医生审核单ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_report_version_report_no` (`report_id`, `version_no`),
  KEY `idx_report_version_report_id` (`report_id`),
  KEY `idx_report_version_doctor_review_id` (`doctor_review_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='舌象报告版本表';

CREATE TABLE IF NOT EXISTS `tongue_report_feature` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '报告特征ID',
  `report_id` BIGINT NOT NULL COMMENT '报告ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `feature_code` VARCHAR(128) NOT NULL COMMENT '标准特征编码',
  `feature_group` VARCHAR(64) DEFAULT NULL COMMENT '特征分组',
  `confidence` DOUBLE DEFAULT NULL COMMENT '置信度',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_report_feature_report_id` (`report_id`),
  KEY `idx_report_feature_user_created` (`user_id`, `created_at`),
  KEY `idx_report_feature_code` (`feature_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='舌象报告特征表';

CREATE TABLE IF NOT EXISTS `tongue_report_evidence` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '报告依据ID',
  `report_id` BIGINT NOT NULL COMMENT '报告ID',
  `chunk_id` VARCHAR(128) DEFAULT NULL COMMENT '知识库切片ID',
  `doc_id` VARCHAR(128) DEFAULT NULL COMMENT '文档ID',
  `title` VARCHAR(255) DEFAULT NULL COMMENT '标题',
  `content` TEXT COMMENT '依据内容',
  `source_uri` VARCHAR(512) DEFAULT NULL COMMENT '来源URI',
  `final_score` DOUBLE DEFAULT NULL COMMENT '最终得分',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_report_evidence_report_id` (`report_id`),
  KEY `idx_report_evidence_chunk_id` (`chunk_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='舌象报告RAG依据表';

CREATE TABLE IF NOT EXISTS `doctor_review_order` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '医生审核单ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `report_id` BIGINT NOT NULL COMMENT '报告ID',
  `doctor_user_id` BIGINT DEFAULT NULL COMMENT '医生用户ID',
  `status` VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED' COMMENT '审核状态',
  `pay_status` VARCHAR(32) DEFAULT 'UNPAID' COMMENT '支付状态',
  `price_amount` DECIMAL(10,2) DEFAULT 0.00 COMMENT '金额',
  `user_remark` TEXT COMMENT '用户备注',
  `accepted_at` DATETIME DEFAULT NULL COMMENT '接单时间',
  `completed_at` DATETIME DEFAULT NULL COMMENT '完成时间',
  `canceled_at` DATETIME DEFAULT NULL COMMENT '取消时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_review_order_user_created` (`user_id`, `created_at`),
  KEY `idx_review_order_doctor_created` (`doctor_user_id`, `created_at`),
  KEY `idx_review_order_status_created` (`status`, `created_at`),
  KEY `idx_review_order_report_id` (`report_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='医生审核订单表';

CREATE TABLE IF NOT EXISTS `doctor_review_comment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '医生审核意见ID',
  `review_order_id` BIGINT NOT NULL COMMENT '审核单ID',
  `doctor_user_id` BIGINT NOT NULL COMMENT '医生用户ID',
  `comment_text` TEXT COMMENT '审核意见',
  `revised_report_json` JSON DEFAULT NULL COMMENT '医生修订报告JSON',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_review_comment_order_id` (`review_order_id`),
  KEY `idx_review_comment_doctor_user_id` (`doctor_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='医生审核意见表';

CREATE TABLE IF NOT EXISTS `doctor_review_attachment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '审核附件ID',
  `review_order_id` BIGINT NOT NULL COMMENT '审核单ID',
  `file_object_id` BIGINT NOT NULL COMMENT '文件对象ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_review_attachment_order_id` (`review_order_id`),
  KEY `idx_review_attachment_file_object_id` (`file_object_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='医生审核附件表';

CREATE TABLE IF NOT EXISTS `user_notification` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '通知ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `type` VARCHAR(64) NOT NULL COMMENT '通知类型',
  `title` VARCHAR(128) DEFAULT NULL COMMENT '标题',
  `content` TEXT COMMENT '内容',
  `payload_json` JSON DEFAULT NULL COMMENT '通知负载',
  `read_at` DATETIME DEFAULT NULL COMMENT '已读时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_notification_user_created` (`user_id`, `created_at`),
  KEY `idx_notification_user_read` (`user_id`, `read_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户通知表';

CREATE TABLE IF NOT EXISTS `system_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `config_key` VARCHAR(128) NOT NULL COMMENT '配置Key',
  `config_value` TEXT COMMENT '配置值',
  `config_group` VARCHAR(64) DEFAULT NULL COMMENT '配置分组',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_system_config_key` (`config_key`),
  KEY `idx_system_config_group` (`config_group`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

CREATE TABLE IF NOT EXISTS `admin_audit_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '审计日志ID',
  `admin_user_id` BIGINT DEFAULT NULL COMMENT '管理员用户ID',
  `action` VARCHAR(64) DEFAULT NULL COMMENT '操作',
  `target_type` VARCHAR(64) DEFAULT NULL COMMENT '对象类型',
  `target_id` BIGINT DEFAULT NULL COMMENT '对象ID',
  `detail_json` TEXT COMMENT '操作详情',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_admin_audit_admin_created` (`admin_user_id`, `created_at`),
  KEY `idx_admin_audit_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员操作审计日志表';

-- 预留：请求链路、分析指标、隐私操作日志。
-- 当前 Java 代码尚未写入这些表，先建表方便后续接入监控与审计。

CREATE TABLE IF NOT EXISTS `request_trace_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '链路日志ID',
  `trace_id` VARCHAR(128) NOT NULL COMMENT '链路追踪ID',
  `request_id` VARCHAR(128) DEFAULT NULL COMMENT '请求ID',
  `user_id` BIGINT DEFAULT NULL COMMENT '用户ID',
  `path` VARCHAR(255) DEFAULT NULL COMMENT '接口路径',
  `method` VARCHAR(16) DEFAULT NULL COMMENT 'HTTP方法',
  `status_code` INT DEFAULT NULL COMMENT 'HTTP状态码',
  `cost_ms` BIGINT DEFAULT NULL COMMENT '耗时毫秒',
  `error_code` VARCHAR(64) DEFAULT NULL COMMENT '错误码',
  `error_message` TEXT COMMENT '错误信息',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_request_trace_id` (`trace_id`),
  KEY `idx_request_trace_user_created` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='请求链路日志表';

CREATE TABLE IF NOT EXISTS `analysis_metric_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '分析指标日志ID',
  `trace_id` VARCHAR(128) DEFAULT NULL COMMENT '链路追踪ID',
  `user_id` BIGINT DEFAULT NULL COMMENT '用户ID',
  `report_id` BIGINT DEFAULT NULL COMMENT '报告ID',
  `task_id` BIGINT DEFAULT NULL COMMENT '任务ID',
  `image_save_ms` BIGINT DEFAULT NULL COMMENT '图片保存耗时',
  `agent_call_ms` BIGINT DEFAULT NULL COMMENT 'Agent调用耗时',
  `model_call_ms` BIGINT DEFAULT NULL COMMENT '模型调用耗时',
  `rag_ms` BIGINT DEFAULT NULL COMMENT 'RAG耗时',
  `llm_ms` BIGINT DEFAULT NULL COMMENT 'LLM耗时',
  `total_ms` BIGINT DEFAULT NULL COMMENT '总耗时',
  `status` VARCHAR(32) DEFAULT NULL COMMENT '状态',
  `error_code` VARCHAR(64) DEFAULT NULL COMMENT '错误码',
  `error_message` TEXT COMMENT '错误信息',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_analysis_metric_trace_id` (`trace_id`),
  KEY `idx_analysis_metric_task_id` (`task_id`),
  KEY `idx_analysis_metric_user_created` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分析性能指标日志表';

CREATE TABLE IF NOT EXISTS `privacy_operation_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '隐私操作日志ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `operation_type` VARCHAR(64) NOT NULL COMMENT '操作类型',
  `target_type` VARCHAR(64) DEFAULT NULL COMMENT '对象类型',
  `target_id` BIGINT DEFAULT NULL COMMENT '对象ID',
  `status` VARCHAR(32) DEFAULT NULL COMMENT '状态',
  `detail_json` JSON DEFAULT NULL COMMENT '操作详情',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_privacy_operation_user_created` (`user_id`, `created_at`),
  KEY `idx_privacy_operation_type` (`operation_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='隐私操作日志表';

CREATE TABLE IF NOT EXISTS `model_service_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'model service config id',
  `service_name` VARCHAR(128) NOT NULL COMMENT 'service name',
  `service_type` VARCHAR(64) NOT NULL COMMENT 'service type, e.g. tongue_image_model',
  `endpoint_url` VARCHAR(512) NOT NULL COMMENT 'endpoint url',
  `api_key_ref` VARCHAR(255) DEFAULT NULL COMMENT 'secret reference, do not store raw secret here',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'enabled flag',
  `timeout_ms` INT NOT NULL DEFAULT 30000 COMMENT 'request timeout in milliseconds',
  `max_concurrency` INT NOT NULL DEFAULT 8 COMMENT 'max concurrent requests',
  `config_json` JSON DEFAULT NULL COMMENT 'provider specific config',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_service_name` (`service_name`),
  KEY `idx_model_service_type_enabled` (`service_type`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='model service config table';

CREATE TABLE IF NOT EXISTS `agent_service_config` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'agent service config id',
  `service_name` VARCHAR(128) NOT NULL COMMENT 'service name',
  `endpoint_url` VARCHAR(512) NOT NULL COMMENT 'agent endpoint url',
  `api_key_ref` VARCHAR(255) DEFAULT NULL COMMENT 'secret reference, do not store raw secret here',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'enabled flag',
  `timeout_ms` INT NOT NULL DEFAULT 60000 COMMENT 'request timeout in milliseconds',
  `max_concurrency` INT NOT NULL DEFAULT 16 COMMENT 'max concurrent requests',
  `health_check_path` VARCHAR(255) DEFAULT NULL COMMENT 'health check path',
  `config_json` JSON DEFAULT NULL COMMENT 'provider specific config',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_service_name` (`service_name`),
  KEY `idx_agent_service_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='agent service config table';

CREATE TABLE IF NOT EXISTS `user_tongue_trend_snapshot` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'trend snapshot id',
  `user_id` BIGINT NOT NULL COMMENT 'user id',
  `range_type` VARCHAR(32) NOT NULL COMMENT 'time range type, e.g. 7d/30d/90d/custom',
  `start_date` DATE DEFAULT NULL COMMENT 'range start date',
  `end_date` DATE DEFAULT NULL COMMENT 'range end date',
  `report_count` INT NOT NULL DEFAULT 0 COMMENT 'report count in range',
  `summary` TEXT COMMENT 'trend summary',
  `feature_stats_json` JSON DEFAULT NULL COMMENT 'feature statistics',
  `agent_summary_json` JSON DEFAULT NULL COMMENT 'agent generated explanation',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  PRIMARY KEY (`id`),
  KEY `idx_trend_snapshot_user_range` (`user_id`, `range_type`, `start_date`, `end_date`),
  KEY `idx_trend_snapshot_user_created` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='user tongue trend snapshot table';

SET FOREIGN_KEY_CHECKS = 1;
