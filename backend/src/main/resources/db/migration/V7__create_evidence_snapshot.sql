CREATE TABLE evidence_snapshot (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES project (id),
    project_input_ids JSONB NOT NULL,
    structured_project_input_ids JSONB NOT NULL,
    file_project_input_ids JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_evidence_snapshot_project_id ON evidence_snapshot (project_id);
