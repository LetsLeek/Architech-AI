CREATE TABLE qa_execution (
    id UUID PRIMARY KEY,
    agent_execution_id UUID NOT NULL REFERENCES agent_execution (id),
    tested_candidate_id UUID NOT NULL REFERENCES website_implementation_candidate (id),
    qa_profile_ref VARCHAR(255) NOT NULL,
    execution_surface_ref VARCHAR(512),
    tool_capability_profile_ref VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_qa_execution_agent_execution_id ON qa_execution (agent_execution_id);
CREATE INDEX idx_qa_execution_tested_candidate_id ON qa_execution (tested_candidate_id);

CREATE TABLE qa_input_snapshot (
    id UUID PRIMARY KEY,
    qa_execution_id UUID NOT NULL REFERENCES qa_execution (id),
    input_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_qa_input_snapshot_qa_execution_id ON qa_input_snapshot (qa_execution_id);

CREATE TABLE evidence_manifest (
    id UUID PRIMARY KEY,
    qa_execution_id UUID NOT NULL REFERENCES qa_execution (id),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_evidence_manifest_qa_execution_id ON evidence_manifest (qa_execution_id);

CREATE TABLE qa_result (
    id UUID PRIMARY KEY,
    qa_execution_id UUID NOT NULL REFERENCES qa_execution (id),
    tested_candidate_id UUID NOT NULL REFERENCES website_implementation_candidate (id),
    qa_profile_ref VARCHAR(255) NOT NULL,
    input_snapshot_id UUID NOT NULL REFERENCES qa_input_snapshot (id),
    evaluation_state VARCHAR(20) NOT NULL,
    domain_results_json TEXT NOT NULL,
    finding_refs_json TEXT NOT NULL,
    authority_issue_refs_json TEXT NOT NULL,
    evaluation_issue_refs_json TEXT NOT NULL,
    policy_evaluation_refs_json TEXT NOT NULL,
    remediation_assessment_refs_json TEXT NOT NULL,
    gate_outcome VARCHAR(20) NOT NULL,
    hold_reasons_json TEXT NOT NULL,
    evidence_manifest_id UUID NOT NULL REFERENCES evidence_manifest (id),
    provenance_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_qa_result_qa_execution_id ON qa_result (qa_execution_id);
CREATE INDEX idx_qa_result_tested_candidate_id ON qa_result (tested_candidate_id);

CREATE TABLE candidate_finding (
    id UUID PRIMARY KEY,
    qa_result_id UUID NOT NULL REFERENCES qa_result (id),
    qa_execution_id UUID NOT NULL,
    tested_candidate_id UUID NOT NULL,
    finding_code VARCHAR(128) NOT NULL,
    primary_domain VARCHAR(64) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    normative_basis_json TEXT NOT NULL,
    summary TEXT NOT NULL,
    diagnostic_details TEXT,
    context_json TEXT,
    evidence_refs_json TEXT NOT NULL,
    fingerprint VARCHAR(512) NOT NULL,
    provenance_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_candidate_finding_qa_result_id ON candidate_finding (qa_result_id);
CREATE INDEX idx_candidate_finding_tested_candidate_id ON candidate_finding (tested_candidate_id);

CREATE TABLE authority_issue (
    id UUID PRIMARY KEY,
    qa_result_id UUID NOT NULL REFERENCES qa_result (id),
    qa_execution_id UUID NOT NULL,
    tested_candidate_id UUID NOT NULL,
    code VARCHAR(64) NOT NULL,
    summary TEXT NOT NULL,
    affected_authority_refs_json TEXT NOT NULL,
    expected_authority_types_json TEXT NOT NULL,
    affected_domains_json TEXT NOT NULL,
    evidence_refs_json TEXT NOT NULL,
    provenance_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_authority_issue_qa_result_id ON authority_issue (qa_result_id);

CREATE TABLE evaluation_issue (
    id UUID PRIMARY KEY,
    qa_result_id UUID NOT NULL REFERENCES qa_result (id),
    qa_execution_id UUID NOT NULL,
    tested_candidate_id UUID NOT NULL,
    code VARCHAR(64) NOT NULL,
    summary TEXT NOT NULL,
    affected_domains_json TEXT NOT NULL,
    required_check_ref VARCHAR(512),
    tool_capability_ref VARCHAR(512),
    execution_surface_ref VARCHAR(512),
    evidence_refs_json TEXT NOT NULL,
    provenance_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_evaluation_issue_qa_result_id ON evaluation_issue (qa_result_id);

CREATE TABLE policy_evaluation (
    id UUID PRIMARY KEY,
    qa_result_id UUID NOT NULL REFERENCES qa_result (id),
    qa_execution_id UUID NOT NULL,
    tested_candidate_id UUID NOT NULL,
    qa_profile_ref VARCHAR(255) NOT NULL,
    subject_type VARCHAR(32) NOT NULL,
    subject_ref VARCHAR(512) NOT NULL,
    policy_rule_ref VARCHAR(512) NOT NULL,
    disposition VARCHAR(20) NOT NULL,
    reason_code VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_policy_evaluation_qa_result_id ON policy_evaluation (qa_result_id);

CREATE TABLE remediation_assessment (
    id UUID PRIMARY KEY,
    previous_finding_id UUID NOT NULL REFERENCES candidate_finding (id),
    tested_candidate_id UUID NOT NULL REFERENCES website_implementation_candidate (id),
    qa_execution_id UUID NOT NULL REFERENCES qa_execution (id),
    qa_result_id UUID NOT NULL REFERENCES qa_result (id),
    status VARCHAR(20) NOT NULL,
    evidence_refs_json TEXT NOT NULL,
    related_new_finding_refs_json TEXT NOT NULL,
    evaluation_issue_refs_json TEXT NOT NULL,
    notes TEXT,
    provenance_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_remediation_assessment_previous_finding_id ON remediation_assessment (previous_finding_id);
CREATE INDEX idx_remediation_assessment_qa_result_id ON remediation_assessment (qa_result_id);
