# Designer Agent V1 — Deterministic Validator Checklist

This checklist complements the authoritative `design-proposal-set` schema and Core validation
contract, mirroring `REQUIREMENTS_VALIDATOR_CHECKLIST.md`'s own structure for the Designer Agent.

## Design Proposal Set

Validate at minimum:

- every `localRef` is globally unique within the complete `design-proposal-set` artifact (spans
  all three proposals - `LocalRefUniquenessValidator`, already generic, needed no new code);
- exactly one root route `/` exists per proposal;
- page routes are unique within each proposal;
- `pageRef` resolves to a page in the same proposal;
- `sectionRef` resolves to a section under the specific page its sibling `pageRef` names;
- `patternRef` resolves to a UI pattern in the same proposal;
- no local reference (`pageRef`/`sectionRef`/`patternRef`) resolves only in a different proposal
  than the one citing it;
- every `requirementRef` exists as a `localRef` in the active `website-requirements` artifact;
- every `customerDataRef` exists as a `localRef` in the active `customer-profile` artifact;
- a `requirementRef`/`customerDataRef` that only exists in the *other* canonical artifact still
  fails - the two are checked against separate permitted sets, never pooled;
- `navigationTarget` variants of type `requirement`/`customer-data` get the same existence check
  as every other `requirementRef`/`customerDataRef` occurrence (the deterministic floor of
  "refers to a semantically actionable canonical destination" - whether a resolvable target is
  genuinely useful to navigate to is a semantic-review question, out of this scope).

## Semantic Scope

Deterministic validation must not attempt to judge design quality, proposal differentiation, or
whether a design decision is a reasonable interpretation of a requirement - those are semantic
questions for AIW-123's semantic reviewer, which never overrides a deterministic failure.

## No Auto-Correction

All validator failures are reports. Validators do not modify candidate output.
