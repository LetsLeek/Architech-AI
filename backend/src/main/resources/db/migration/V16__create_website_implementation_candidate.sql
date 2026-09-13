CREATE TABLE website_implementation_candidate (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES project (id),
    agent_execution_id UUID NOT NULL REFERENCES agent_execution (id),
    source_design_artifact_version_ref VARCHAR(512) NOT NULL,
    source_design_proposal_local_ref VARCHAR(512) NOT NULL,
    runtime_profile_ref VARCHAR(255) NOT NULL,
    repository_state_ref VARCHAR(255) NOT NULL,
    implementation_summary TEXT NOT NULL,
    implementation_anchors TEXT NOT NULL,
    functional_bindings TEXT NOT NULL,
    unresolved_issues TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (agent_execution_id)
);

CREATE INDEX idx_website_implementation_candidate_project_id ON website_implementation_candidate (project_id);
