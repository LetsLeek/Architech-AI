CREATE TABLE integration_contract (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES project (id),
    contract_ref VARCHAR(512) NOT NULL,
    version INT NOT NULL,
    safe_contract_content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (project_id, contract_ref)
);

CREATE INDEX idx_integration_contract_project_id ON integration_contract (project_id);
