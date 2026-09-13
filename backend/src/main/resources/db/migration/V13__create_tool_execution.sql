CREATE TABLE tool_execution (
    id UUID PRIMARY KEY,
    agent_execution_id UUID NOT NULL REFERENCES agent_execution (id),
    capability VARCHAR(50) NOT NULL,
    tool_name VARCHAR(255) NOT NULL,
    correction_cycle INT NOT NULL,
    status VARCHAR(50) NOT NULL,
    diagnostic_summary TEXT,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_tool_execution_agent_execution_id ON tool_execution (agent_execution_id);
