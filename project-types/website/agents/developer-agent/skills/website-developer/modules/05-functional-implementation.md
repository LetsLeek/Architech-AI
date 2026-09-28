# Module 05 — Functional Implementation

## Model

`Visible Function → Local App Behavior → External Integration`

## Functional Binding states

- `IMPLEMENTED_LOCAL`: the authorized function is fully implemented with local/client behavior; no external Integration Contract is required.
- `IMPLEMENTED_BOUND`: the implementation is bound to one authorized Integration Contract.
- `UNBOUND`: the candidate can remain technically viable, but an authorized function cannot be externally bound because of missing/invalid integration authority or missing upstream information.

A correctly-shaped `UNBOUND` functional binding looks exactly like this:

```json
{
  "requirementRef": "req-xyz",
  "designLocalRefs": ["sec-example"],
  "status": "UNBOUND",
  "blockerCode": "MISSING_INTEGRATION_CONTRACT"
}
```

- No other property is permitted on a functional binding. There is no `note` field or any other free-text field — the schema rejects any property not explicitly declared. Explain context in `UnresolvedIssue.summary` instead, never inline on the binding.
- `blockerCode` must be exactly one of `MISSING_INTEGRATION_CONTRACT`, `INVALID_INTEGRATION_CONTRACT`, or `MISSING_UPSTREAM_INFORMATION` — never a free-text string. `integrationContractRef` is required only when `blockerCode` is `INVALID_INTEGRATION_CONTRACT`; omit it otherwise.
- A partial result — some functional bindings `IMPLEMENTED_LOCAL`/`IMPLEMENTED_BOUND`, one or more genuinely `UNBOUND` — still uses top-level `resultType: "IMPLEMENTATION_READY"`. Do not switch the whole result to `resultType: "BLOCKED"` for this. `BLOCKED` is a separate, all-or-nothing top-level shape (`blockers[]` only, no `implementationSummary`/`implementationAnchors`/`functionalBindings`/`unresolvedIssues`) used only when nothing usable was produced at all. Reporting one binding as `UNBOUND` inside an otherwise-complete `IMPLEMENTATION_READY` result is the correct way to hand off partial, real, completed work.

## Forms

Implement fields, labels, local validation and local interaction as designed/authorized. Submission is real only when an Integration Contract authorizes it. Never show false sent/success behavior for an unbound external action.

## External-function examples

Booking, payment, authentication, social APIs, dynamic map services and server-side submission require explicit authority. A location fact does not imply a map integration. A social link does not imply a social feed/API.

## Storage

Use browser/local storage only when needed by authorized behavior and privacy/security boundaries. Do not invent tracking/analytics/persistence.

## Tests

Add meaningful tests for nontrivial local logic and authorized adapters. Do not trigger production side effects in Developer verification.
