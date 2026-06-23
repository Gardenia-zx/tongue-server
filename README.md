# tongue-server MVP

Java 后端负责 App 业务、用户、文件、任务、报告、医生审核与 Python Agent 编排之间的生产版主链路：

1. 手机号验证码登录，JWT 鉴权
2. 接收前端上传的舌象图片并保存文件元数据
3. 创建异步分析任务，立即返回 `task_id` 和 `report_id`
4. 后台调用 Python Agent `POST /api/v1/agent/run`
5. 接收舌象识别、RAG 和报告结果
6. 持久化报告、特征、知识库依据和报告版本
7. 提供报告查询、趋势统计、医生审核、通知、管理后台和隐私删除接口

## 启动前置服务

先启动 Python Agent：

```powershell
cd D:\tongue\tongue-agent
python run_agent_server.py --host 127.0.0.1 --port 8000
```

如果模型服务使用 Hugging Face Space，确认 `tongue-agent` 的模型接口和 token 已经配置好。

## 启动 Java 后端

### 初始化 MySQL

方式一：你自己在 DataGrip 里创建 database，然后执行完整建表脚本：

```text
D:\tongue\tongue-server\sql\schema_mysql.sql
```

方式二：如果希望命令行顺手创建 database 和本地开发账号，可以执行：

```powershell
cd D:\tongue\tongue-server
mysql -u root -p < .\sql\init_database.sql
```

脚本会创建：

```text
database: tongue_app
username: tongue_app
password: tongue_app_123456
```

说明：

- `schema_mysql.sql` 是完整业务表建表语句，适合 DataGrip 执行。
- `init_database.sql` 只负责创建 database 和账号。
- 如果你手动建库和账号，可以通过环境变量覆盖：

```powershell
$env:MYSQL_URL="jdbc:mysql://127.0.0.1:3306/tongue_app?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
$env:MYSQL_USERNAME="你的账号"
$env:MYSQL_PASSWORD="你的密码"
```

建议使用项目内 Maven settings，避免使用全局 `D:\Maven\...\maven-repo` 中损坏或不可写的缓存：

```powershell
cd D:\tongue\tongue-server
mvn -s .\maven-settings-local.xml spring-boot:run -Dspring-boot.run.profiles=local
```

默认端口：

```text
http://127.0.0.1:8080
```

## 编译检查

当前代码已通过：

```powershell
cd D:\tongue\tongue-server
mvn compile
```

如果 `package` 或 `spring-boot:run` 报 `D:\Maven\...\maven-repo` 权限或坏缓存问题，继续使用：

```powershell
mvn -s .\maven-settings-local.xml compile
mvn -s .\maven-settings-local.xml spring-boot:run
```

## 后端接口

### 登录

开发环境默认验证码：

```powershell
curl.exe -X POST "http://127.0.0.1:8080/api/auth/sms/send" `
  -H "Content-Type: application/json" `
  -d "{\"phone\":\"13800000000\"}"
```

```powershell
curl.exe -X POST "http://127.0.0.1:8080/api/auth/sms/login" `
  -H "Content-Type: application/json" `
  -d "{\"phone\":\"13800000000\",\"code\":\"123456\"}"
```

正式接口使用：

```text
Authorization: Bearer <access_token>
```

本地联调兼容：

```text
X-User-Id: 1
```

### 创建异步舌象分析任务

```text
POST /api/tongue/analyze
Content-Type: multipart/form-data
```

表单字段：

- `image`: 舌象图片，必填
- `threadId`: 会话 ID，可选
- `conversationId`: 对话 ID，可选
- `clientTraceId`: 前端 trace ID，可选

返回：

```json
{
  "report_id": 20001,
  "task_id": 30001,
  "status": "PENDING"
}
```

命令行测试：

```powershell
curl.exe -X POST "http://127.0.0.1:8080/api/tongue/analyze" `
  -H "X-User-Id: 1" `
  -F "image=@D:\tongue\tongue_delivery\tongue.jpg"
```

### 查询任务和报告

```text
GET /api/tongue/tasks/{taskId}
GET /api/tongue/reports/{reportId}
GET /api/tongue/reports
GET /api/tongue/reports/{reportId}/versions
GET /api/tongue/reports/{reportId}/features
GET /api/tongue/reports/{reportId}/evidence
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
```

### 趋势、通知、管理与隐私

```text
GET  /api/tongue/trends/overview
GET  /api/tongue/trends/features
GET  /api/tongue/trends/timeline
GET  /api/notifications
POST /api/notifications/{notificationId}/read
POST /api/privacy/delete-reports
POST /api/privacy/delete-account
GET  /api/admin/users
GET  /api/admin/tasks
GET  /api/admin/reports
```

## 启动前端

```powershell
cd D:\tongue\tongue-web
python -m http.server 5173 --bind 127.0.0.1
```

浏览器打开：

```text
http://127.0.0.1:5173
```
