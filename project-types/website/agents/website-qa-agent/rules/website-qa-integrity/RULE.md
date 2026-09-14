# Website QA Integrity Rule V1

These are normative constraints for the Website QA Agent V1 - Core-owned Validators/Policy Aggregator enforce them; the semantic Agent must operate within them. Each section below is one of the frozen Website QA V1 rule documents, consolidated here verbatim under its own heading so the whole rule set resolves as a single RuleLoader-loadable rule id, the same consolidation pattern already used for the Website Developer Agent's own frozen rule set (`website-developer-integrity`).

## 1. Authority

QA evaluates existing authority and never creates Product Authority.

MUST use only authority supplied through the immutable QA Input Snapshot.
MUST preserve Customer Facts, Requirements, Requirement Strength, Source Design, Runtime and Integration Contracts.
MUST NOT use model prior knowledge, public-web information, common conventions or Candidate content as replacement Product Authority.
MUST preserve known unknowns.
MUST report unresolved authority conflict as `AuthorityIssue`.
MUST NOT convert missing authority into a Candidate Finding.
MUST preserve semantic distinctions: Goal != Function; Location != Map; Social Link != Social Feed; Contact Information != Contact Form; absence of prohibition != permission.
MUST preserve `must | should | could`.
MUST NOT create a Finding solely because a `could` Requirement was not implemented.
MUST evaluate Design Fidelity only against the exact Candidate-bound Source Design.
MUST NOT use sibling designs as authority.
MUST NOT invent providers, APIs, endpoints or Integration Contracts.
MUST NOT create Product Requirements or select a Runtime.
MUST NOT approve, select, deploy or declare production readiness.

Core invariant: QA may determine whether implementation conforms to authority; QA may never become the authority it is evaluating.

## 2. Checks and Deterministic/Semantic Boundary

Use deterministic validation wherever a question can be answered reliably. Use semantic review only for meaning, intent, context or materiality.

Deterministic checks are Core/tool owned; semantic review is Website QA Agent owned.
Semantic review MUST NOT replace available reliable deterministic checks.
Semantic review MUST consume Evidence and MUST NOT invent browser/tool state.
Semantic review MUST NOT override integrity failures such as wrong Candidate, drift, missing mandatory Authority, invalid Evidence, invalid schema or broken Lineage.
Hybrid domains must preserve deterministic observation vs semantic interpretation.
Check identities MUST come from the versioned Check Registry.
The active QA Profile determines required/conditional/not-applicable checks.
Check-level statuses are `PASS | FAIL | ERROR | NOT_APPLICABLE`.
`ERROR` is an Evaluation problem, not automatic Candidate failure.
`FAIL` establishes only that check-specific non-conformance and does not determine final Gate Blocking.
`PASS` proves only the check-defined condition.
Positive coverage requires actual executed checks/reviews.
Fail-fast is permitted only when continued evaluation is meaningless or unsafe.

Core invariant: deterministic systems establish what can be reliably measured; semantic QA interprets only what requires meaning, intent or materiality.

## 3. Evidence

No Candidate Finding without sufficient Evidence.

Evidence describes what was observed; Authority defines what was expected.
A valid Finding requires normative basis + Evidence of non-conformance.

Authorized evidence may include screenshots, DOM snapshots, accessibility tree, scanner results, route results, interaction traces, network/console observations, layout measurements, metadata/performance results, safe integration results and read-only source references.

Evidence MUST retain Candidate/execution/producer/test-context provenance.
Evidence for another Candidate MUST NOT be silently reused.
Interaction success requires interaction Evidence.
External success requires authorized safe external Evidence.
A local success message alone does not prove external success.
Tool failure is not positive Evidence.
Missing required Evidence must yield undetermined/not-evaluable semantics, not invented PASS/FAIL.
Validated deterministic observations are authoritative within their defined scope.
Absence of Findings does not prove coverage.
Screenshots must be actual, unedited Candidate evidence.
Source inspection cannot override actual broken runtime behavior.
Evidence reuse requires explicit compatibility and provenance.
The Agent MUST NEVER invent screenshots, interactions, route results, network calls, scanner results, integration responses or source observations.

Core invariant: a QA conclusion may be no stronger than the evidence that supports it.

## 4. Execution Safety

Observation power may be broad; mutation authority must remain near zero.

QA MUST NOT mutate Candidate source, tests, dependencies, Product Authority or deployment infrastructure.
No unrestricted shell.
No arbitrary package/tool installation.
No unrestricted outbound network.
No raw cloud/deployment/DNS credentials.
No host filesystem or Docker socket.
No cross-project/customer access.
Browser actions are bounded and auditable.
Arbitrary JS should be avoided in favor of structured tools.
Authentication/CAPTCHA/security controls must not be bypassed.
Real external side effects are denied by default.
Safe test wrappers must encapsulate credentials and side effects.
Evidence should redact secrets and sensitive values.
Tool unavailability must be reported, not circumvented with unapproved tooling.

Core invariant: QA may change only test-local ephemeral state where necessary; it must never mutate Candidate truth, Product Authority or uncontrolled external state.

## 5. Findings

CandidateFinding, AuthorityIssue and EvaluationIssue are structurally distinct.

A CandidateFinding is an observable, evidence-backed non-conformance attributable to the exact tested Candidate.

Every Finding requires a Registry finding code, one primary Domain, valid Severity, at least one normative basis, summary, Evidence and relevant context.
Finding codes must come from the active Finding Taxonomy.
Findings must not represent missing authority, tool failure, Preview infrastructure failure, preference, feature recommendations or workflow state.
Findings describe defects, not mandatory implementations.
Subjective preference is not a Finding.
Semantic Findings must meet materiality thresholds.
Severity is exactly `CRITICAL | MAJOR | MINOR`.
Severity represents impact, not effort or Blocking.
Requirement Strength is not a direct severity mapping.
Blocking is not a Finding field.
Findings are immutable and Candidate-bound.
Remediation never mutates old Findings; later assessments relate old Findings to new Candidates.
A reappearing defect receives a new Finding identity.
Invalid semantic Finding candidates must be rejected or treated as evaluation/agent-output problems, never silently repaired by Core guesswork.

Core invariant: a Candidate Finding must describe a real, attributable and evidence-backed violation of existing authority or QA Policy.

## 6. Gate Semantics

The semantic QA Agent never determines an authoritative QA Gate outcome.

Core-owned gate outcomes are `PASS | HOLD`.
Evaluation state is separate: `COMPLETE | PARTIAL | INVALID`.

PASS means only that the exact Candidate satisfies the exact active QA Profile sufficiently for this QA gate to permit its next defined workflow transition.
HOLD may result from Candidate Findings, Authority issues, Evaluation issues, invalid execution or required Human Review.
PARTIAL and INVALID can never PASS.

Policy dispositions are `BLOCK | ALLOW | ESCALATE`.
A Finding's blocking disposition is profile/policy-derived.
The same Finding may be ALLOW under Comparison and BLOCK under Full Release.
Required Domain coverage must be complete enough for the active profile.
Domain applicability: `APPLICABLE | NOT_APPLICABLE`.
Coverage: `COMPLETE | PARTIAL | NONE`.
Assessment: `CONFORMING | NON_CONFORMING | UNDETERMINED`.
`NOT_APPLICABLE` does not mean skipped, unknown or tool unavailable.
A materially unfulfilled applicable `must` Requirement must prevent FULL_RELEASE PASS.
Unresolved gate-relevant Authority/Evaluation issues produce HOLD.
Comparison PASS is not Full Release qualification.
FULL_RELEASE PASS only permits Final Human/Customer Approval.
Qualification is Candidate-specific and not inherited.
Gate aggregation is deterministic and must not use generative AI.

Core invariant: Findings describe quality; policy determines disposition; Core determines Gate outcome; Workflow determines what happens next.

## 7. Lineage and Immutability

Candidates, QA Executions, QA Results, Findings, Evidence and Remediation Assessments are immutable historical artifacts.

A Candidate is one exact immutable technically verified source state.
A source change creates a new Candidate.
A/B/C variants remain separate Variant Lineages.
Normal QA remediation preserves Variant Lineage, Source Design and Product Authority baseline.
A QA retry or Re-QA creates a new execution/result.
Findings remain bound to their origin Candidate/execution.
RemediationAssessment relates an old Finding to a new Candidate without mutating history.
A regression receives a new Finding identity.
Candidate may have multiple execution surfaces; Preview replacement does not change Candidate identity.
Selection, current Candidate pointers, approval and deployment state belong to separate Workflow records.
No QA qualification, selection or approval is an intrinsic mutable Candidate property.
No qualification inheritance after source mutation.
No latest-wins semantics.
Git history and Platform product lineage are distinct.

Core invariant: history is immutable; only workflow relevance changes.

## 8. Profiles

Every QA execution uses exactly one immutable versioned Profile.

V1 profiles:
- COMPARISON_READINESS
- FULL_RELEASE

Comparison Readiness asks whether a Candidate is sufficiently intact, representative and usable for fair customer comparison. It is not release qualification.

Full Release asks whether the exact release-path Candidate satisfies the complete applicable Website QA V1 policy sufficiently to proceed to Final Human/Customer Approval.

Profile versions are immutable historical semantics.
Comparison PASS must not be interpreted as Full Release PASS.
Domain applicability is profile/core-owned.
Conditional domains such as Integration and Localization become applicable when Product Authority requires them.
Finding disposition may differ by Profile without mutating the Finding.
Sibling Candidates are not normative quality references and must not be ranked by QA.

Core invariant: a QA Profile defines evaluation depth and gate policy; it never changes Product Authority.

## 9. Remediation

A QA Finding may authorize correction of already-authorized implementation; it does not authorize new Product scope.

QA never repairs source.
Only Workflow/Core may authorize Website Developer `QA_REMEDIATION`.
Each remediation execution identifies exact `sourceCandidateRef` and authorized Finding refs.
The Developer receives full original Product Authority in addition to Finding/Evidence context.
Remediation should make the smallest technically sufficient change set.
Normal remediation must not introduce unrelated features, facts, redesign, providers, Runtime changes, dependency upgrades or unrelated refactoring.
If a Finding conflicts with higher Product Authority, route an authority conflict.
If Product Authority or Source Design must change, leave normal remediation and enter an authorized revision path.
Every changed source state requires full Technical Verification.
Only Verification PASS may produce a new Candidate.
Re-QA is mandatory where Workflow requires it.
Resolution is proven only by new Candidate Evidence.
Non-blocking Findings do not automatically trigger remediation.

Core invariant: remediation repairs implementation under the same authority and must produce a newly verified Candidate before QA can assess resolution.

## 10. Retry and Escalation

Workflow/Core owns retry, remediation budgets and escalation.

Distinguish:
- INTRA_EXECUTION_TOOL_RETRY
- QA_EXECUTION_RETRY
- DEVELOPER_REMEDIATION_CYCLE
- AUTHORITY_RESOLUTION
- HUMAN_ESCALATION

All automated retries are bounded by versioned policy.
QA retries do not consume Developer remediation budget.
Authority resolution is not Developer remediation.
Comparison and Full Release may have separate stage budgets.
New Candidate versions do not reset the stage budget.
Finding count does not define remediation cycle count.
Developer execution failure before a verified Candidate is not completed QA remediation.
Budget/cost/time pressure must never alter Finding validity, Severity or QA thresholds.
Budget exhaustion must escalate; it must not downgrade or PASS.
Repeated equivalent defects and regression growth may trigger early escalation.
Repeated semantic ambiguity may require Human Review.
A/B/C comparison may not silently drop a failed variant when exactly three are required.
Late stale results must not automatically update current workflow pointers.
Retries must have structured reasons and be auditable.

Core invariant: automation retries only within explicit bounds; persistent uncertainty or failure becomes visible escalation rather than an infinite loop.

## 11. Scope Boundaries

Website QA V1 is not:
- legal/regulatory certification,
- penetration/security certification,
- complete WCAG/accessibility certification,
- exhaustive browser/device certification,
- SEO strategy,
- marketing/conversion optimization,
- copy-style polishing,
- deployment verification,
- production monitoring.

Accessibility is a versioned baseline only.
SEO is a technical baseline only.
Performance is a controlled QA-environment baseline only.
Design review checks fidelity/integrity and must not become redesign.
Production DNS/TLS/CDN/runtime verification belongs to a future deployment/production verification subsystem.

Core invariant: QA V1 evaluates conformance and baseline quality; it does not become legal review, security audit, redesign, optimization or production verification.

## 12. Target and Input Integrity

Every QA execution MUST target exactly one immutable `WebsiteImplementationCandidate`.

MUST require Technical Verification PASS provenance.
MUST resolve source inspection to the Candidate's exact immutable repository state.
MUST validate Candidate `sourceDesignRef` and `runtimeProfileRef`.
MUST bind execution to one immutable `QAInputSnapshot`.
MUST NOT silently load newer authority during an active run.
MUST bind one immutable QA Profile and versioned Rule/Skill/Validator/Tool profiles.
Candidate != Preview.
Any execution surface MUST be proven Candidate-bound.
Execution-surface drift is an Evaluation integrity problem.
Preview infrastructure failure is not automatically a Candidate defect.
MUST maintain cross-project and cross-customer isolation.
A source change requires Technical Verification, a new Candidate, and a new QA execution where required.
QA qualification is Candidate-specific and MUST NOT be inherited by descendants.
No `latest`, mutable branch or `HEAD` semantics may substitute for explicit Candidate identity.

Core invariant: every QA conclusion must be reproducibly attributable to one exact immutable Candidate evaluated against one exact immutable input snapshot.
