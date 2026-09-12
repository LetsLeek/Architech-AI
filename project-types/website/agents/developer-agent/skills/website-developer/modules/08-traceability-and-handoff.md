# Module 08 — Traceability and Handoff

## Finalization order

1. Confirm implementation is materially complete under Developer authority.
2. Confirm the current source state has passed your applicable internal checks.
3. Build `ImplementationAnchor` entries from the actual final source tree.
4. Build one proposal-scoped `FunctionalBinding` for each expected functional requirement.
5. Record only candidate-compatible `UnresolvedIssue` conditions.
6. Emit either `IMPLEMENTATION_READY` or `BLOCKED`.

## Anchors

Every canonical Page and Section in the target Proposal must have exactly one anchor entry. `ELEMENT` and `UI_PATTERN` anchors are optional when useful. Targets use normalized repository-relative real source paths; symbols are optional and must be real if supplied. Anchors are navigation aids, not ownership or exhaustive-file claims.

## Functional bindings

Use only canonical functional requirement refs in the target Proposal's authorized functional scope. Do not invent bindings for purely technical interactions without a canonical functional requirement.

## Issues

`UnresolvedIssue` is only for candidate-compatible upstream/integration conditions. Known implementation defects, verification failures, infrastructure errors, refactor debt and generic TODOs do not belong there.

## BLOCKED

A blocked result contains one or more `DeveloperBlocker` causes and no pseudo-Candidate metadata. Completion blockers are nonlocal; Developer-owned defects are execution failures, not blockers.

## Stop writing

Submission of a final semantic handoff ends normal write authority until Core explicitly opens a correction phase.
