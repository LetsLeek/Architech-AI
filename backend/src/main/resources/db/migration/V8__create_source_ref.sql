CREATE TABLE source_ref (
    id UUID PRIMARY KEY,
    evidence_snapshot_id UUID NOT NULL REFERENCES evidence_snapshot (id),
    ref VARCHAR(64) NOT NULL,
    source_item_id UUID NOT NULL,
    origin VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (evidence_snapshot_id, ref),
    UNIQUE (evidence_snapshot_id, source_item_id)
);

CREATE INDEX idx_source_ref_evidence_snapshot_id ON source_ref (evidence_snapshot_id);
