# MoonCell Gateway - Reliable AI Message Broker

MoonCell Gateway 是一个企业级 AI 消息中间件与网关。
它不仅负责负载均衡，更是一个**高可靠的任务调度系统**。

## 核心架构 v3.0 (Reliable Message Queue)

为了解决“网络抖动丢消息”和“服务重启丢任务”的问题，我们重构了核心链路：

1.  **WAL (Write-Ahead Log)**:
    - 所有请求在处理前，先持久化到 `chat_task` 表 (State: PENDING)。
    - 服务重启后，自动扫描未完成任务重新入队。
2.  **异步队列消费**:
    - `TaskProducer`: Controller 接收请求 -> 存库 -> 推入内存 `BlockingQueue`。
    - `TaskConsumer`: 后台线程池消费队列 -> 申请资源 -> 执行 HTTP 请求。
3.  **资源独占锁 (Resource Locking)**:
    - 路由前先锁定目标 `ModelInstance` (Semaphore)，防止超卖或过载。
    - 无资源时 Fast Fail。
4.  **ACK 机制**:
    - 任务处理完成后更新 DB 状态为 `COMPLETED`。
    - 失败则标记 `FAILED` 并记录重试次数。
5.  **SSE 桥接**:
    - 虽然内部是异步队列，但通过 `StreamBridge` 将消费者产生的流实时推回给前端，保持打字机体验。

## 快速开始

### 1. 启动服务
```bash
start_gateway.bat
# 需确保数据库连接正常 (默认为 H2)
```

### 2. 调用接口 (OpenFeign / HTTP)

**POST** `/v1/chat/completions`

请求体现在必须符合标准的 OpenAI 格式（会自动映射为 `OpenAiRequest` 对象）：

```json
{
  "model": "gpt-4",
  "messages": [{"role": "user", "content": "Hello"}],
  "stream": true
}
```

### 3. 查看任务状态 (DB)

你可以查询 `chat_task` 表来审计所有的请求记录：
```sql
SELECT * FROM chat_task WHERE status = 'FAILED';
```

## 模块说明
- `api`: 定义 OpenFeign 接口与 DTO。
- `core/task`: 任务调度核心 (Manager, Consumer)。
- `core/balancer`: 资源管理 (LoadBalancer, ResourceLock)。
- `web`: 统一入口 Controller。
