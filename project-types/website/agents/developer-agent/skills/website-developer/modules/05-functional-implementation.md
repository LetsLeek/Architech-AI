# Module 05 — Functional Implementation

## Model

`Visible Function → Local App Behavior → External Integration`

## Functional Binding states

- `IMPLEMENTED_LOCAL`: the authorized function is fully implemented with local/client behavior; no external Integration Contract is required.
- `IMPLEMENTED_BOUND`: the implementation is bound to one authorized Integration Contract.
- `UNBOUND`: the candidate can remain technically viable, but an authorized function cannot be externally bound because of missing/invalid integration authority or missing upstream information.

## Forms

Implement fields, labels, local validation and local interaction as designed/authorized. Submission is real only when an Integration Contract authorizes it. Never show false sent/success behavior for an unbound external action.

## External-function examples

Booking, payment, authentication, social APIs, dynamic map services and server-side submission require explicit authority. A location fact does not imply a map integration. A social link does not imply a social feed/API.

## Storage

Use browser/local storage only when needed by authorized behavior and privacy/security boundaries. Do not invent tracking/analytics/persistence.

## Tests

Add meaningful tests for nontrivial local logic and authorized adapters. Do not trigger production side effects in Developer verification.
