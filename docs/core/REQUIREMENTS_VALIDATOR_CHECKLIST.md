# Requirements Agent V1 — Deterministic Validator Checklist

This checklist complements the authoritative schemas and Core validation contract.

## Customer Profile

Validate at minimum:

- every `sourceRef` exists in the active evidence snapshot;
- every `localRef` is globally unique within the Customer Profile artifact;
- every opening-hours `locationRef` resolves to an existing location;
- every provenance `targetRef` resolves to a referencable Customer Profile entity;
- provenance fields are valid logical fields for their target/singleton context;
- fixed price: `amount` present, `maxAmount` absent;
- from price: `amount` present, `maxAmount` absent;
- range price: `amount` and `maxAmount` present and `maxAmount >= amount`;
- on-request price: `amount` and `maxAmount` absent;
- when currency is present, it is a recognized ISO 4217 code;
- phone values pass only conservative plausibility checks; do not require E.164;
- website values do not require the platform to invent a URI scheme;
- opening-hours interval `from < to` for V1 normalized intervals;
- intervals for the same scheduled day do not overlap;
- `closedDays` do not overlap scheduled days;
- duplicate weekday definitions are not contradictory;
- ambiguous unknowns contain supporting `sourceRefs`;
- conflict statements preserve at least two materially distinct competing statements.

## Website Requirements

Validate at minimum:

- every `sourceRef` exists in the active evidence snapshot;
- every `localRef` is globally unique within the Website Requirements artifact;
- every `affects` reference resolves to an existing referencable requirement item;
- language codes are valid BCP 47 tags;
- language codes are not duplicated;
- content requirement `type=custom` requires `customType`;
- content requirement `type!=custom` forbids `customType`;
- functional requirement `type=custom` requires `customType`;
- functional requirement `type!=custom` forbids `customType`;
- ambiguous unknowns contain supporting `sourceRefs`.

## Cross-Artifact

Deterministic validation must not attempt to solve semantic truth.

Machine-checkable cross-artifact rules should enforce reference integrity and contract consistency. Semantic questions such as whether a requirement unnecessarily duplicates a profile fact may be checked by the agent's final consistency pass or a separately authorized semantic reviewer, but such review never overrides deterministic failures.

## No Auto-Correction

All validator failures are reports. Validators do not modify candidate output.
