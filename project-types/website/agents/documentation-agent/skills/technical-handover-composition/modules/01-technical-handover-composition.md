# Skill — Technical Handover composition

**Applicable only to:** `TECHNICAL_HANDOVER@1.0.0`. `FULL_RELEASE` may be PASS or HOLD. This is Developer-facing implementation handover, **not** a deployment-ready operational runbook.

- `IMPLEMENTATION_OVERVIEW`: bound Candidate and implementation topology, not inferred "best practice" architecture.
- `RUNTIME_AND_ARCHITECTURE`: state only authorized runtime/framework/profile facts. React/TypeScript/Vite alone do not justify unstated hosting/operations architecture.
- `ROUTING`: explain important verified structure while deferring exact route enumeration to Core implementation manifest.
- `FUNCTIONAL_BEHAVIOR`: synthesize Requirements + actual Candidate/Bindings. `UNBOUND` is not `IMPLEMENTED_BOUND`.
- `INTEGRATIONS`: describe authorized IntegrationContracts and nonsecret dependencies; never output credential values or invent vendors.
- `TECHNICAL_CONSTRAINTS`: separate intentional runtime/design constraints from outstanding QA defects.
- `QA_AND_OUTSTANDING_ISSUES`: preserve current bound QA Gate and findings; refer to Core-owned QA register.
- `DEPLOYMENT_INFORMATION`: use scoped DeploymentRecord if actually bound; missing authority is not proof deployment never occurred.
- `MAINTENANCE_NOTES`: describe only recorded change processes and technical constraints; no invented operational runbook or SLA.
- `REFERENCES`: use Core-composed deterministic manifests and refs, not made-up git or provider links.

Core inserts required deterministic reports (`ARTIFACT_VERSION_MANIFEST`, `IMPLEMENTATION_MANIFEST`, `FUNCTIONAL_BINDING_REPORT`, `INTEGRATION_REFERENCE_REPORT`, `QA_FINDING_REGISTER`) according to profile. Never recreate or edit their exact content. Required sections always appear; empty model blocks are allowed only where profile and Core deterministic content justify it.

Related: `DOC-PROF-005`, `DOC-AUTH-004`–`009`, `DOC-GEN-003`–`005`.
