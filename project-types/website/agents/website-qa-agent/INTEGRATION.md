# Integration Guide

This package is repository-neutral.

Recommended integration sequence:

1. Map `website-qa-agent-v1/` into the repository location used by the existing Requirements / Designer / Developer agent packages.
2. Keep `agent.yaml`, `AGENT.md`, `rules/`, `skills/`, `schemas/`, `registries/`, `profiles/`, and `policies/` versioned together.
3. Implement `core/VALIDATORS.md` and `core/POLICY_AGGREGATOR.md` in Platform/Core code. Do not move those decisions into the LLM agent.
4. Add the Website Developer operation documented in `developer-extension/QA_REMEDIATION.md`.
5. Bind actual platform schema IDs / artifact references to the opaque refs used here.
6. Add schema validation and negative fixtures to CI before enabling automatic QA transitions.
7. Only after Core validation, enable:
   Candidate -> QA -> Finding -> Workflow-authorized QA_REMEDIATION -> Technical Verification -> new Candidate -> Re-QA.
8. Customer comparison exposure must require three valid Comparison Readiness PASS results when the workflow contract promises A/B/C variants.
9. Full Release PASS must route only to Final Human/Customer Approval, never directly to Deployment.

Do not silently rename semantic enum values during integration. If repository conventions require other names, add an explicit migration/mapping layer and version it.
