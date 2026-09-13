-- AIW-152: project-scoped, deterministically resolvable assets (images, logos, ...) referenced
-- by canonical Customer Profile/Design Proposal content. Mirrors file_project_input's own
-- shape (V5) for raw content storage - asset_ref is the stable, opaque identifier a canonical
-- artifact's own content refers to, unique per project so cross-project lookup is structurally
-- impossible (a lookup is always scoped by (project_id, asset_ref) together, never asset_ref alone).
CREATE TABLE project_asset (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES project (id),
    asset_ref VARCHAR(512) NOT NULL,
    filename VARCHAR(1024) NOT NULL,
    content_type VARCHAR(255),
    size_bytes BIGINT NOT NULL,
    content BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (project_id, asset_ref)
);

CREATE INDEX idx_project_asset_project_id ON project_asset (project_id);
