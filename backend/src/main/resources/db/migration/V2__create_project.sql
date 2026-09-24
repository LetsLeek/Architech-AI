CREATE TABLE project (
    id UUID PRIMARY KEY,
    project_type VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
