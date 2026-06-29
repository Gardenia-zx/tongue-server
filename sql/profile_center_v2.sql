-- Personal profile center V2 one-time migration for MySQL 8.
-- Skip columns that already exist when applying manually.

ALTER TABLE `user_profile`
    ADD COLUMN `birth_date` DATE NULL AFTER `age`,
    ADD COLUMN `email` VARCHAR(128) NULL AFTER `birth_date`,
    ADD COLUMN `avatar_file_name` VARCHAR(160) NULL AFTER `avatar_file_id`,
    ADD COLUMN `profile_note` VARCHAR(500) NULL AFTER `health_focus`,
    ADD COLUMN `height_cm` DOUBLE NULL AFTER `profile_note`,
    ADD COLUMN `weight_kg` DOUBLE NULL AFTER `height_cm`,
    ADD COLUMN `sleep_hours` DOUBLE NULL AFTER `weight_kg`,
    ADD COLUMN `exercise_frequency` VARCHAR(32) NULL AFTER `sleep_hours`,
    ADD COLUMN `dietary_preference` TEXT NULL AFTER `exercise_frequency`,
    ADD COLUMN `answer_detail_level` VARCHAR(16) NULL AFTER `dietary_preference`,
    ADD COLUMN `use_health_profile` BIT(1) NULL AFTER `answer_detail_level`,
    ADD COLUMN `use_history_reports` BIT(1) NULL AFTER `use_health_profile`,
    ADD COLUMN `use_long_term_memory` BIT(1) NULL AFTER `use_history_reports`,
    ADD COLUMN `tongue_reminder_enabled` BIT(1) NULL AFTER `use_long_term_memory`,
    ADD COLUMN `tongue_reminder_time` VARCHAR(8) NULL AFTER `tongue_reminder_enabled`,
    ADD COLUMN `sleep_reminder_enabled` BIT(1) NULL AFTER `tongue_reminder_time`,
    ADD COLUMN `sleep_reminder_time` VARCHAR(8) NULL AFTER `sleep_reminder_enabled`;

UPDATE `user_profile`
SET `answer_detail_level` = COALESCE(`answer_detail_level`, 'STANDARD'),
    `use_health_profile` = COALESCE(`use_health_profile`, 1),
    `use_history_reports` = COALESCE(`use_history_reports`, 1),
    `use_long_term_memory` = COALESCE(`use_long_term_memory`, 0),
    `tongue_reminder_enabled` = COALESCE(`tongue_reminder_enabled`, 0),
    `tongue_reminder_time` = COALESCE(`tongue_reminder_time`, '09:00'),
    `sleep_reminder_enabled` = COALESCE(`sleep_reminder_enabled`, 0),
    `sleep_reminder_time` = COALESCE(`sleep_reminder_time`, '22:00');
