CREATE TABLE evidence_record (
    id UUID PRIMARY KEY,
    evidence_manifest_id UUID NOT NULL REFERENCES evidence_manifest (id),
    qa_execution_id UUID NOT NULL REFERENCES qa_execution (id),
    tested_candidate_id UUID NOT NULL REFERENCES website_implementation_candidate (id),
    kind VARCHAR(32) NOT NULL,
    producer_ref VARCHAR(512) NOT NULL,
    route VARCHAR(2048),
    viewport_ref VARCHAR(512),
    locale VARCHAR(64),
    interaction_state VARCHAR(512),
    content TEXT NOT NULL,
    reused_from_evidence_id UUID REFERENCES evidence_record (id),
    reuse_justification TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_evidence_record_evidence_manifest_id ON evidence_record (evidence_manifest_id);
CREATE INDEX idx_evidence_record_qa_execution_id ON evidence_record (qa_execution_id);
CREATE INDEX idx_evidence_record_tested_candidate_id ON evidence_record (tested_candidate_id);
