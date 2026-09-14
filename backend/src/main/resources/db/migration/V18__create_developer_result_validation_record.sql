CREATE TABLE developer_result_validation_record (
    id UUID PRIMARY KEY,
    agent_execution_id UUID NOT NULL REFERENCES agent_execution (id),
    valid BOOLEAN NOT NULL,
    issues TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_developer_result_validation_record_agent_execution_id ON developer_result_validation_record (agent_execution_id);
