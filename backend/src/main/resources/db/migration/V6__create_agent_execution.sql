CREATE TABLE agent_execution (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES project (id),
    agent_id VARCHAR(255) NOT NULL,
    agent_version INTEGER NOT NULL,
    status VARCHAR(50) NOT NULL,
    provider VARCHAR(255),
    model VARCHAR(255),
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    cost_usd NUMERIC(12, 6),
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ
);

CREATE INDEX idx_agent_execution_project_id ON agent_execution (project_id);
