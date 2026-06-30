# tongue-server

Java 后端服务，负责用户、认证、舌象分析任务、报告、趋势、医生审核、通知、健康计划和前端 Agent 聊天网关。它是三端前端访问的唯一业务后端，同时负责调用 `tongue-agent` 完成模型识别、RAG 和自然语言解释。

## 技术栈

- Java 8
- Spring Boot 2.7.18
- Spring Web / Validation / Data JPA
- MySQL 8
- Redis
- Maven

默认服务地址：

```text
http://127.0.0.1:8080
```

## 目录结构

```text
tongue-server
├─ src/main/java/com/tongue/server
│  ├─ admin          管理端接口和系统配置
│  ├─ agent          Agent 聊天 V2 网关、上下文和消息持久化
│  ├─ auth           登录、用户、医生资料和 JWT
│  ├─ client         Python Agent 调用客户端
│  ├─ common         通用响应、异常和错误码
│  ├─ config         Spring 配置属性
│  ├─ controller     舌象分析任务和报告接口
│  ├─ health         健康计划、打卡、执行总结
│  ├─ notification   用户通知
│  ├─ privacy        隐私删除
│  ├─ review         医生审核订单
│  ├─ storage        文件上传和本地存储
│  ├─ tongue         舌象报告、特征、证据、快照实体
│  └─ trend          趋势分析
├─ src/main/resources
│  ├─ application.yml
│  └─ application-local.yml
├─ sql               MySQL 建表和补充脚本
└─ pom.xml
```

## 本地启动顺序

推荐顺序：

1. 启动 MySQL 和 Redis。
2. 初始化 `tongue_app` 数据库。
3. 启动 `tongue-agent`，默认端口 `8000`。
4. 启动 `tongue-server`，默认端口 `8080`。
5. 启动 `tongue-web`。

## 数据库初始化

如果本地还没有数据库，先执行：

```powershell
cd D:\tongue\tongue-server
mysql -u root -p < .\sql\init_database.sql
```

该脚本会创建：

```text
database: tongue_app
username: tongue_app
password: tongue_app_123456
```

然后执行主表结构：

```powershell
mysql -u tongue_app -p tongue_app < .\sql\schema_mysql.sql
```

如果你是在旧库上升级，按需要补跑这些脚本：

```text
sql/context_schema_mysql.sql      Agent 上下文和消息表
sql/agent_chat_v2.sql             Agent Chat V2 会话表
sql/profile_center_v2.sql         个人资料中心补充字段
sql/seed_admin_local.sql          本地管理员种子数据
```

本地 profile 使用 `application-local.yml`，JPA 默认 `ddl-auto=validate`，所以缺表时会直接启动失败。这是好事，先补齐 SQL 再启动。

## 关键环境变量

可以直接用 `application-local.yml`，也可以用环境变量覆盖：

```powershell
$env:MYSQL_URL="jdbc:mysql://127.0.0.1:3306/tongue_app?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
$env:MYSQL_USERNAME="tongue_app"
$env:MYSQL_PASSWORD="tongue_app_123456"
$env:REDIS_HOST="127.0.0.1"
$env:REDIS_PORT="6379"
$env:TONGUE_AGENT_BASE_URL="http://127.0.0.1:8000"
$env:TONGUE_JWT_SECRET="change-me-to-a-local-secret-with-at-least-32-characters"
```

常用配置说明：

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `server.port` | `8080` | Java 后端端口 |
| `MYSQL_URL` | `jdbc:mysql://127.0.0.1:3306/tongue_app...` | MySQL 连接 |
| `MYSQL_USERNAME` | `root` / local 为 `tongue_app` | 数据库用户名 |
| `MYSQL_PASSWORD` | local 为 `tongue_app_123456` | 数据库密码 |
| `REDIS_HOST` | `127.0.0.1` | Redis 地址 |
| `TONGUE_AGENT_BASE_URL` | `http://127.0.0.1:8000` | Python Agent 地址 |
| `TONGUE_AGENT_READ_TIMEOUT_MILLIS` | `240000` | 报告生成读取超时 |
| `TONGUE_DEV_SMS_CODE` | `123456` | 本地短信验证码 |
| `TONGUE_ALLOW_DEV_USER_ID` | `true` | 本地是否允许 `X-User-Id` |
| `TONGUE_PUBLIC_BASE_URL` | 空 | 文件公开访问前缀 |

## 启动服务

建议使用仓库内 Maven settings，避免全局 Maven 缓存损坏导致依赖拉取异常：

```powershell
cd D:\tongue\tongue-server
mvn -s .\maven-settings-local.xml spring-boot:run -Dspring-boot.run.profiles=local
```

如果不使用 local profile：

```powershell
mvn -s .\maven-settings-local.xml spring-boot:run
```

## 常用开发命令

```powershell
# 编译
mvn -s .\maven-settings-local.xml compile

# 测试
mvn -s .\maven-settings-local.xml test

# 打包
mvn -s .\maven-settings-local.xml package
```

## 认证方式

本地开发可以用短信验证码：

```powershell
curl.exe -X POST "http://127.0.0.1:8080/api/auth/sms/send" `
  -H "Content-Type: application/json" `
  -d "{\"phone\":\"13800000000\"}"

curl.exe -X POST "http://127.0.0.1:8080/api/auth/sms/login" `
  -H "Content-Type: application/json" `
  -d "{\"phone\":\"13800000000\",\"code\":\"123456\"}"
```

正式请求使用：

```text
Authorization: Bearer <access_token>
```

本地联调也支持：

```text
X-User-Id: 1
```

前提是 `TONGUE_ALLOW_DEV_USER_ID=true`。

## 核心业务流程

### 舌象分析两阶段流程

```text
前端上传图片
  -> POST /api/tongue/analyze/prepare
  -> 创建 report 和 task，状态 WAITING_STATE
  -> 前端收集近 3 天状态快照
  -> POST /api/tongue/tasks/{taskId}/state-snapshot
  -> Java 异步调用 Python Agent
  -> 保存报告、特征、证据、质量评分、结构化内容
  -> 前端轮询 GET /api/tongue/tasks/{taskId}
```

旧接口仍保留：

```text
POST /api/tongue/analyze
```

它会跳过状态补充，直接启动分析。

### Agent 聊天流程

```text
前端 /api/v2/agent/chat
  -> Java 读取会话、历史、报告绑定上下文
  -> Java 调用 Python Agent
  -> Java 净化 content 和 structured_content
  -> 保存用户消息和助手消息
  -> 返回前端可展示内容
```

### 健康计划流程

```text
报告详情
  -> POST /api/health-plans/from-report/{reportId}/draft
  -> 用户编辑 DRAFT
  -> POST /api/health-plans/{planId}/review
  -> 可直接 activate，或生成更具体 7 天计划
  -> POST /api/health-plans/{planId}/activate
  -> 每日打卡和执行总结
```

## 主要接口

### 认证与用户

```text
POST   /api/auth/sms/send
POST   /api/auth/sms/login
POST   /api/auth/logout
GET    /api/users/me
PUT    /api/users/me/profile
POST   /api/users/me/avatar
DELETE /api/users/me/avatar
GET    /api/public/profile-avatars/{fileName}
```

### 舌象分析与报告

```text
POST   /api/tongue/analyze
POST   /api/tongue/analyze/prepare
GET    /api/tongue/tasks/{taskId}
POST   /api/tongue/tasks/{taskId}/retry
POST   /api/tongue/tasks/{taskId}/state-snapshot
GET    /api/tongue/dashboard
GET    /api/tongue/reports
GET    /api/tongue/reports/{reportId}
GET    /api/tongue/reports/{reportId}/versions
GET    /api/tongue/reports/{reportId}/features
GET    /api/tongue/reports/{reportId}/evidence
POST   /api/tongue/reports/{reportId}/export
POST   /api/tongue/reports/compare
DELETE /api/tongue/reports/{reportId}
```

### 趋势

```text
GET /api/tongue/trends/overview
GET /api/tongue/trends/features
GET /api/tongue/trends/timeline
GET /api/tongue/trends/series
```

### Agent 聊天

```text
POST /api/v2/agent/chat
POST /internal/agent/reports/{reportId}/sections
```

`/internal/agent/reports/{reportId}/sections` 给 Python Agent 拉取报告结构化片段使用，需要内部 key。

### 健康计划与打卡

```text
GET  /api/health-plans/current
GET  /api/health-plans/{planId}
GET  /api/health-plans/{planId}/execution-summary
POST /api/health-plans/from-report/{reportId}
POST /api/health-plans/from-report/{reportId}/draft
PUT  /api/health-plans/{planId}/draft
POST /api/health-plans/{planId}/review
POST /api/health-plans/{planId}/generate-detailed
POST /api/health-plans/{planId}/activate
POST /api/health-plans/{planId}/close
GET  /api/checkins
POST /api/checkins/today
GET  /api/checkins/summary
```

### 医生审核

```text
POST /api/reviews
GET  /api/reviews/{reviewId}
GET  /api/reviews/my
POST /api/reviews/{reviewId}/cancel
GET  /api/doctor/reviews
POST /api/doctor/reviews/{reviewId}/accept
POST /api/doctor/reviews/{reviewId}/submit
GET  /api/doctors
GET  /api/doctors/{doctorId}
PUT  /api/doctors/me/profile
```

### 通知、隐私、文件

```text
GET    /api/notifications
POST   /api/notifications/{notificationId}/read
POST   /api/notifications/read-all
POST   /api/privacy/delete-reports
POST   /api/privacy/delete-account
POST   /api/files/tongue-images
GET    /api/files/{fileId}/view-url
DELETE /api/files/{fileId}
```

### 管理端

```text
POST /api/admin/auth/login
GET  /api/admin/users
GET  /api/admin/doctors
POST /api/admin/doctors/{doctorId}/approve
POST /api/admin/doctors/{doctorId}/reject
GET  /api/admin/reports
GET  /api/admin/tasks
GET  /api/admin/reviews
GET  /api/admin/system/config
PUT  /api/admin/system/config
GET  /api/admin/audit-logs
GET  /api/admin/metrics/tasks
GET  /api/admin/metrics/errors
```

## 文件存储

默认本地路径：

```text
D:/tongue/storage/uploads
D:/tongue/storage/reports
```

上传限制：

```text
max-file-size: 10MB
max-request-size: 12MB
```

## 常见问题

### 启动时报数据库表不存在

local profile 使用 `ddl-auto=validate`。执行 `sql/schema_mysql.sql`，旧库再补跑增量 SQL。

### 前端提示无法连接后端

确认 Java 正在监听 `http://127.0.0.1:8080`，并且前端登录页保存的后端地址不是 Vite 端口 `5173/5174/5175`。

### 舌象分析一直失败

按顺序检查：

1. `tongue-agent` 是否启动：`http://127.0.0.1:8000/api/v1/health`
2. `TONGUE_AGENT_BASE_URL` 是否正确。
3. Python Agent 的模型网关 key 是否配置。
4. 舌象图片是否超过 10MB。

### Maven 依赖异常

优先使用：

```powershell
mvn -s .\maven-settings-local.xml compile
```

## 开发约定

- Java 负责确定性业务逻辑、权限校验、数据落库和兜底。
- Python Agent 负责识别编排、RAG、自然语言解释和健康计划 AI 评估。
- 前端只消费稳定 DTO，不解析后端内部 JSON。
- 新接口默认返回 `ApiResponse<T>`。
- 用户健康内容不要写入无必要日志。
