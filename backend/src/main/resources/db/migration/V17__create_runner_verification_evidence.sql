CREATE TABLE runner_verification_run (
    id UUID PRIMARY KEY,
    agent_execution_id UUID NOT NULL REFERENCES agent_execution (id),
    repository_state_ref VARCHAR(255) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_runner_verification_run_agent_execution_id ON runner_verification_run (agent_execution_id);

CREATE TABLE runner_verification_gate (
    id UUID PRIMARY KEY,
    runner_verification_run_id UUID NOT NULL REFERENCES runner_verification_run (id),
    gate_name VARCHAR(100) NOT NULL,
    gate_order INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    detail TEXT,
    UNIQUE (runner_verification_run_id, gate_name)
);

CREATE INDEX idx_runner_verification_gate_run_id ON runner_verification_gate (runner_verification_run_id);
