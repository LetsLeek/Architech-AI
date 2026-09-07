CREATE TABLE project_input (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES project (id),
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_project_input_project_id ON project_input (project_id);
