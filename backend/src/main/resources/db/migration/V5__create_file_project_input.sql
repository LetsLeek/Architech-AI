CREATE TABLE file_project_input (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES project (id),
    filename VARCHAR(1024) NOT NULL,
    content_type VARCHAR(255),
    size_bytes BIGINT NOT NULL,
    content BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_file_project_input_project_id ON file_project_input (project_id);
