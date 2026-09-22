CREATE TABLE qa_remediation_budget (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    variant_lineage_ref VARCHAR(255) NOT NULL,
    stage VARCHAR(32) NOT NULL,
    remediation_cycles_used INTEGER NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uq_qa_remediation_budget_project_lineage_stage
    ON qa_remediation_budget (project_id, variant_lineage_ref, stage);

-- Retrofits optimistic locking onto AIW-177's own ComparisonReadinessSlot pointer - "Late/stale
-- concurrent results cannot overwrite newer workflow pointers without state validation" (AIW-181).
ALTER TABLE comparison_readiness_slot ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
