# 中医舌象健康管理系统 MVP 联调接口文档

版本：V1.0  
日期：2026-06-18

## 1. 联调目标

本阶段目标是完成完整产品闭环：

```text
前端上传舌象图片
  -> Java 后端保存图片并创建分析任务
  -> Java 后端调用 Python Agent
  -> Python Agent 调用舌象模型服务
  -> Python Agent 基于识别特征进行 RAG 检索和报告生成
  -> Java 后端保存 draft_report
  -> 前端展示分析结果
```

MVP 阶段暂不追求复杂权限、支付、医生审核、长期趋势分析，但接口结构要为后续扩展保留稳定字段。

## 2. 服务边界

### 2.1 前端 tongue-web

职责：

- 上传舌象图片；
- 展示分析进度；
- 展示舌象分析报告；
- 展示错误信息；
- 后续支持历史报告列表和详情页。

### 2.2 Java 后端 tongue-server

职责：

- 接收前端图片上传；
- 保存图片文件；
- 创建报告记录和任务记录；
- 调用 Python Agent；
- 保存 Python 返回的 `draft_report`；
- 向前端返回统一响应；
- 后续提供历史报告查询、报告删除、趋势分析接口。

### 2.3 Python Agent tongue-agent

职责：

- 意图识别；
- 调用舌象模型服务；
- 标准化舌象识别指标；
- 根据识别特征生成 RAG 检索 query；
- 调用 RAG；
- 生成结构化 `draft_report`；
- 返回分析结果给 Java 后端。

### 2.4 模型服务 tongue_model

职责：

- 接收图片；
- 使用 YOLO 模型识别舌象特征；
- 返回模型检测结果；
- 不负责报告解释、不负责 RAG、不负责用户会话。

## 3. 总体调用链路

```text
POST /api/tongue/analyze
前端 -> Java

POST /api/v1/agent/run
Java -> Python Agent

POST /v1/tongue/predict
Python Agent -> 舌象模型服务

Java 保存 draft_report
Java -> 前端返回分析结果
```

## 4. 前端调用 Java：舌象分析接口

### 4.1 接口

```http
POST /api/tongue/analyze
Content-Type: multipart/form-data
```

### 4.2 请求参数

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| image | file | 是 | 舌象图片，支持 jpg/png/webp |
| userId | long | 是 | 当前用户 ID |
| conversationId | string | 否 | 会话 ID |
| threadId | string | 否 | 前端已有会话 ID；为空时后端生成 |
| clientTraceId | string | 否 | 前端链路追踪 ID |

### 4.3 前端示例

```javascript
const formData = new FormData();
formData.append("image", file);
formData.append("userId", "10001");
formData.append("conversationId", "conv_001");

const res = await fetch("/api/tongue/analyze", {
  method: "POST",
  body: formData,
});

const data = await res.json();
```

### 4.4 Java 返回前端成功响应

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "report_id": 20001,
    "task_id": 30001,
    "thread_id": "tongue_thread_10001_20001",
    "status": "COMPLETED",
    "summary": "本次图像识别到的主要舌象特征包括：白苔...",
    "detected_feature_codes": [
      "coating.color.white"
    ],
    "rag_query": "白苔 苔白 舌苔发白 舌苔颜色 舌象观察 一般健康知识",
    "draft_report": {
      "schema_version": "1.0",
      "report_type": "tongue_analysis_mvp",
      "report_status": "DRAFT",
      "report_id": 20001,
      "user_id": 10001,
      "thread_id": "tongue_thread_10001_20001",
      "task_id": 30001,
      "image_info": {},
      "standard_features": {},
      "feature_summary": "本次图像识别到的主要舌象特征包括：白苔。",
      "rag_query": "白苔 苔白 舌苔发白 舌苔颜色 舌象观察 一般健康知识",
      "rag_grounded": true,
      "rag_evidence": [],
      "summary": "完整报告说明文本",
      "health_notes": [],
      "risk_disclaimer": "以上内容用于一般健康知识说明和健康管理参考，不能替代医生诊断。",
      "versions": {},
      "metadata": {}
    }
  }
}
```

## 5. Java 调 Python Agent

### 5.1 接口

```http
POST http://127.0.0.1:8000/api/v1/agent/run
Content-Type: application/json; charset=utf-8
```

### 5.2 Java 请求 Python Agent

```json
{
  "schema_version": "1.0",
  "request_id": "uuid",
  "trace_id": "trace_20260618_001",
  "user_id": 10001,
  "thread_id": "tongue_thread_10001_20001",
  "conversation_id": "conv_001",
  "report_id": 20001,
  "task_id": 30001,
  "task_version": 1,
  "message": {
    "role": "user",
    "content_type": "text",
    "content": "我想做一次舌象分析",
    "attachments": [
      {
        "file_id": 50001,
        "file_type": "image",
        "purpose": "tongue_image"
      }
    ]
  },
  "client_context": {
    "page": "tongue_analyze",
    "active_report_id": 20001,
    "device_type": "web",
    "locale": "zh-CN",
    "extra": {
      "image_path": "D:\\tongue\\storage\\uploads\\10001\\20001\\tongue.jpg"
    }
  },
  "options": {
    "memory": {
      "can_read": true,
      "can_write": false
    }
  }
}
```

MVP 阶段 Java 可以先传 `image_path`。正式部署时建议改成：

```json
{
  "image_url": "https://server-domain/files/50001"
}
```

原因：

- 本地路径只适合单机联调；
- 多机部署时 Python Agent 不一定能访问 Java 服务器本地磁盘；
- URL 更适合服务间调用。

### 5.3 Python Agent 成功响应

```json
{
  "schema_version": "1.0",
  "request_id": "uuid",
  "trace_id": "trace_20260618_001",
  "thread_id": "tongue_thread_10001_20001",
  "conversation_id": "conv_001",
  "report_id": 20001,
  "task_id": 30001,
  "status": "COMPLETED",
  "intent_result": {},
  "message": {
    "role": "assistant",
    "content_type": "text",
    "content": "本次图像识别到的主要舌象特征包括：白苔..."
  },
  "next_action": {
    "type": "RESPOND_TO_USER",
    "payload": {
      "status": "COMPLETED",
      "route_target": "tongue_analysis_subgraph",
      "grounded": true,
      "hit_count": 3,
      "detected_feature_codes": [
        "coating.color.white"
      ],
      "rag_query": "白苔 苔白 舌苔发白 舌苔颜色 舌象观察 一般健康知识",
      "draft_report": {}
    }
  },
  "state_snapshot": {
    "current_node": "tongue_report_node",
    "tongue_analysis": {
      "detected_feature_codes": [
        "coating.color.white"
      ],
      "rag_query": "白苔 苔白 舌苔发白 舌苔颜色 舌象观察 一般健康知识",
      "rag_grounded": true,
      "rag_hit_count": 3,
      "has_draft_report": true,
      "report_status": "DRAFT",
      "rag_evidence_count": 3
    }
  }
}
```

Java 后端应优先保存：

```json
next_action.payload.draft_report
```

并把 `message.content` 作为前端展示摘要。

## 6. Python Agent 调模型服务

### 6.1 本地模型服务

```env
TONGUE_MODEL_BASE_URL=http://127.0.0.1:9100
TONGUE_MODEL_API_KEY=
TONGUE_MODEL_BEARER_TOKEN=
```

### 6.2 Hugging Face 私有 Space

```env
TONGUE_MODEL_BASE_URL=https://gardenia77-tongue-model.hf.space
TONGUE_MODEL_API_KEY=
TONGUE_MODEL_BEARER_TOKEN=hf_xxx
```

### 6.3 模型接口

```http
POST /v1/tongue/predict
Content-Type: multipart/form-data
```

请求：

```text
file=@tongue.jpg
```

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "image": "tongue.jpg",
    "features": [
      {
        "class_id": 9,
        "feature": "白苔",
        "confidence": 0.9368,
        "bbox_xyxy": [142.81, 403.45, 1575.72, 2048.74]
      }
    ],
    "llm_summary": "白苔，置信度0.9368"
  }
}
```

## 7. Java 后端建议表设计

### 7.1 tongue_image_file

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 图片 ID |
| user_id | bigint | 用户 ID |
| original_filename | varchar | 原始文件名 |
| storage_path | varchar | 本地存储路径 |
| content_type | varchar | 图片类型 |
| file_size | bigint | 文件大小 |
| purpose | varchar | tongue_image |
| created_at | datetime | 创建时间 |

### 7.2 tongue_report

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 报告 ID |
| user_id | bigint | 用户 ID |
| thread_id | varchar | Agent 会话 ID |
| task_id | bigint | 任务 ID |
| report_status | varchar | DRAFT / FINAL |
| summary | text | 报告摘要 |
| feature_summary | varchar | 特征摘要 |
| detected_feature_codes | json | 识别特征 code |
| draft_report_json | json | Python 返回完整报告 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |

### 7.3 tongue_analysis_task

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint | 任务 ID |
| report_id | bigint | 报告 ID |
| user_id | bigint | 用户 ID |
| status | varchar | PENDING / RUNNING / COMPLETED / FAILED |
| request_id | varchar | Python request_id |
| trace_id | varchar | trace_id |
| error_code | varchar | 错误码 |
| error_message | text | 错误信息 |
| started_at | datetime | 开始时间 |
| finished_at | datetime | 完成时间 |

## 8. Java 返回前端错误格式

统一错误响应：

```json
{
  "code": 40001,
  "message": "图片格式不支持",
  "data": null,
  "trace_id": "trace_20260618_001"
}
```

建议错误码：

| code | 含义 |
|---:|---|
| 0 | 成功 |
| 40001 | 图片格式不支持 |
| 40002 | 图片大小超过限制 |
| 40003 | 图片为空 |
| 40901 | 同一会话正在处理中 |
| 50001 | Python Agent 调用失败 |
| 50002 | 舌象模型服务失败 |
| 50003 | RAG 知识库不可用 |
| 50004 | 报告保存失败 |

## 9. 前端展示字段建议

前端 MVP 展示：

```json
{
  "summary": "完整报告说明文本",
  "feature_summary": "本次图像识别到的主要舌象特征包括：白苔。",
  "detected_feature_codes": ["coating.color.white"],
  "standard_features": {},
  "rag_evidence": [],
  "health_notes": [],
  "risk_disclaimer": "以上内容用于一般健康知识说明和健康管理参考，不能替代医生诊断。"
}
```

页面模块：

1. 图片预览；
2. 分析状态；
3. 识别特征；
4. 报告摘要；
5. 健康知识说明；
6. 参考依据；
7. 风险提示。

## 10. 联调测试用例

### 10.1 正常流程

输入：

- 上传 `tongue.jpg`

预期：

- Java 保存图片成功；
- Python Agent 返回 `COMPLETED`；
- `current_node = tongue_report_node`；
- `draft_report.report_status = DRAFT`；
- 前端展示报告。

### 10.2 模型服务失败

操作：

- 关闭模型服务或配置错误 URL。

预期：

- Java 返回明确错误；
- task 状态为 `FAILED`；
- 前端提示稍后重试。

### 10.3 RAG 无命中

操作：

- 使用无法匹配知识库的特征 query。

预期：

- 报告仍然生成；
- `rag_grounded = false`；
- `rag_evidence = []`。

### 10.4 并发测试

场景：

- 5 个不同用户同时上传；
- 同一个 `thread_id` 连续点击两次分析。

预期：

- 不同用户可并发；
- 同一个 `thread_id` 第二个请求返回 409 或排队处理；
- 不出现报告覆盖。

## 11. 性能指标

建议记录：

| 指标 | 目标 |
|---|---:|
| 图片上传保存 | < 500ms |
| Python Agent 总耗时 | < 15s |
| 模型识别耗时 | < 8s |
| RAG 检索耗时 | < 2s |
| LLM 生成耗时 | < 8s |
| 总接口 p95 | < 20s |

日志建议记录：

```text
trace_id
request_id
user_id
report_id
task_id
image_save_ms
agent_call_ms
model_call_ms
rag_ms
llm_ms
total_ms
status
error_code
```

## 12. MVP 联调顺序

推荐顺序：

1. 前端直接调用 Java 上传接口；
2. Java 保存图片到本地；
3. Java 调 Python Agent，传 `image_path`；
4. Python Agent 调 Hugging Face 或本地模型服务；
5. Python Agent 返回 `draft_report`；
6. Java 保存报告 JSON；
7. 前端展示报告；
8. 再改成 `image_url` 模式；
9. 最后做并发和性能测试。

## 13. 当前关键约定

- MVP 阶段 Python Agent 是分析编排中心；
- Java 后端是文件、报告、用户和任务的持久化中心；
- 模型服务只做图像识别；
- 前端不直接调用 Python Agent，也不直接调用模型服务；
- Java 后端保存 `draft_report_json`，后续报告解释和趋势分析都基于它。
