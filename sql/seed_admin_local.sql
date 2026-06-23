-- 本地开发管理员账号种子数据。
-- 默认账号：admin
-- 默认密码：admin123456
--
-- password_hash 支持两种写法：
-- 1. MySQL SHA2 纯 hex：SHA2('admin123456', 256)
-- 2. 带前缀格式：CONCAT('sha256:', SHA2('admin123456', 256))

INSERT INTO `admin_user` (`username`, `password_hash`, `status`)
VALUES ('admin', SHA2('admin123456', 256), 'ACTIVE')
ON DUPLICATE KEY UPDATE
  `password_hash` = VALUES(`password_hash`),
  `status` = VALUES(`status`);
