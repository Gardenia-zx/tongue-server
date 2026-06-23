-- MySQL 初始化脚本
-- 执行账号：建议用 root 或拥有 CREATE USER / CREATE DATABASE 权限的账号执行

CREATE DATABASE IF NOT EXISTS tongue_app
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'tongue_app'@'localhost'
  IDENTIFIED BY 'tongue_app_123456';

CREATE USER IF NOT EXISTS 'tongue_app'@'%'
  IDENTIFIED BY 'tongue_app_123456';

GRANT ALL PRIVILEGES ON tongue_app.* TO 'tongue_app'@'localhost';
GRANT ALL PRIVILEGES ON tongue_app.* TO 'tongue_app'@'%';

FLUSH PRIVILEGES;

-- 验证：
-- SHOW DATABASES LIKE 'tongue_app';
-- SELECT user, host FROM mysql.user WHERE user = 'tongue_app';
