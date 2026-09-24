CREATE TABLE documentation_context (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES project (id),
    candidate_id UUID NOT NULL REFERENCES website_implementation_candidate (id),
    qa_result_id UUID NOT NULL REFERENCES qa_result (id),
    profile_ref VARCHAR(64) NOT NULL,
    context_version INTEGER NOT NULL,
    content_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_documentation_context_candidate_id ON documentation_context (candidate_id);
