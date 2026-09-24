CREATE TABLE documentation_line (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES project (id),
    profile_ref VARCHAR(64) NOT NULL,
    locale VARCHAR(16) NOT NULL,
    current_package_version_id UUID,
    current_revision INTEGER NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uq_documentation_line_project_profile_locale ON documentation_line (project_id, profile_ref, locale);

CREATE TABLE documentation_package_version (
    id UUID PRIMARY KEY,
    documentation_line_id UUID NOT NULL REFERENCES documentation_line (id),
    revision INTEGER NOT NULL,
    project_id UUID NOT NULL REFERENCES project (id),
    context_id UUID NOT NULL REFERENCES documentation_context (id),
    profile_ref VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    supersedes_package_version_id UUID REFERENCES documentation_package_version (id),
    content_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_documentation_package_version_line_id ON documentation_package_version (documentation_line_id);
CREATE UNIQUE INDEX uq_documentation_package_version_line_idempotency ON documentation_package_version (documentation_line_id, idempotency_key);
