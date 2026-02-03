CREATE TABLE IF NOT EXISTS provider (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE, 
    description VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS model_instance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider_id BIGINT NOT NULL,
    model_name VARCHAR(100) NOT NULL, 
    url VARCHAR(500) NOT NULL UNIQUE,
    api_key VARCHAR(500),
    weight INT DEFAULT 10,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 新增：任务持久化表
CREATE TABLE IF NOT EXISTS chat_task (
    id VARCHAR(64) PRIMARY KEY, -- UUID
    idempotency_key VARCHAR(128) UNIQUE, -- 幂等键
    model VARCHAR(100) NOT NULL,
    request_json TEXT NOT NULL, 
    status VARCHAR(20) NOT NULL, -- PENDING, RUNNING, COMPLETED, FAILED
    retry_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO provider (name, description) VALUES ('openai', 'Official OpenAI API');
INSERT INTO provider (name, description) VALUES ('azure', 'Microsoft Azure OpenAI');
