CREATE TABLE comparison_readiness_barrier (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_comparison_readiness_barrier_project_id ON comparison_readiness_barrier (project_id);

CREATE TABLE comparison_readiness_slot (
    id UUID PRIMARY KEY,
    barrier_id UUID NOT NULL REFERENCES comparison_readiness_barrier (id),
    variant_lineage_ref VARCHAR(255) NOT NULL,
    current_candidate_id UUID NOT NULL REFERENCES website_implementation_candidate (id),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_comparison_readiness_slot_barrier_id ON comparison_readiness_slot (barrier_id);
CREATE UNIQUE INDEX uq_comparison_readiness_slot_barrier_lineage ON comparison_readiness_slot (barrier_id, variant_lineage_ref);
