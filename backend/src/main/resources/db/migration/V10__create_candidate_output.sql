CREATE TABLE candidate_output (
    id UUID PRIMARY KEY,
    agent_execution_id UUID NOT NULL REFERENCES agent_execution (id),
    artifact_type VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_candidate_output_agent_execution_id ON candidate_output (agent_execution_id);
