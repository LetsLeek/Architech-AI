CREATE TABLE artifact (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES project (id),
    type VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (project_id, type)
);

CREATE TABLE artifact_version (
    id UUID PRIMARY KEY,
    artifact_id UUID NOT NULL REFERENCES artifact (id),
    version_number INTEGER NOT NULL,
    agent_execution_id UUID NOT NULL REFERENCES agent_execution (id),
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (artifact_id, version_number)
);

CREATE INDEX idx_artifact_project_id ON artifact (project_id);
CREATE INDEX idx_artifact_version_artifact_id ON artifact_version (artifact_id);
