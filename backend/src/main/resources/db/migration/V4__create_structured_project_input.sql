CREATE TABLE structured_project_input (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES project (id),
    fields JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_structured_project_input_project_id ON structured_project_input (project_id);
