CREATE TABLE documentation_render (
    id UUID PRIMARY KEY,
    package_version_id UUID NOT NULL REFERENCES documentation_package_version (id),
    format VARCHAR(16) NOT NULL,
    renderer_version VARCHAR(128) NOT NULL,
    content TEXT NOT NULL,
    content_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_documentation_render_package_version_id ON documentation_render (package_version_id);
