CREATE TABLE initial_generation_batch (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    design_proposal_set_artifact_version_id UUID NOT NULL REFERENCES artifact_version (id),
    status VARCHAR(20) NOT NULL,
    escalation_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_initial_generation_batch_project_id ON initial_generation_batch (project_id);

CREATE TABLE initial_generation_slot (
    id UUID PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES initial_generation_batch (id),
    proposal_local_ref VARCHAR(255) NOT NULL,
    current_agent_execution_id UUID NOT NULL REFERENCES agent_execution (id),
    retries_used INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (batch_id, proposal_local_ref)
);

CREATE INDEX idx_initial_generation_slot_batch_id ON initial_generation_slot (batch_id);
