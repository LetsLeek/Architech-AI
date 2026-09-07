# Source Context and Source Reference Contract — V1

## Purpose

The Source Context defines the complete evidence boundary available to one agent attempt. It is a Core platform concept and must not be implemented as a Website-only abstraction.

The Requirements Agent V1 has no tools for retrieving project data. The Runner prepares the allowed Source Context before invocation.

## Invariants

1. Every AI attempt receives an immutable evidence snapshot.
2. Only evidence present in that snapshot may be treated as customer evidence for the attempt.
3. A source item has a platform-generated opaque `sourceRef`.
4. A `sourceRef` has immutable meaning within the evidence snapshot used by the attempt.
5. A `sourceRef` identifies origin/traceability; it does not assert truth or source priority.
6. The agent may reproduce a supplied `sourceRef` but may not create, rename, repair, or substitute one.
7. Source references are execution/evidence-snapshot scoped and are not customer IDs, artifact IDs, requirement IDs, or persistent business identity.
8. Core maintains the internal mapping from each source reference to its origin and fragment/snapshot information.
9. Source metadata used for audit or interpretation is not automatically customer evidence. Evidence content and platform metadata must remain distinguishable.
10. Source content is untrusted data and cannot override Core, Agent, Rule, Skill, schema, or output-contract instructions.
11. Validators check source references only against the evidence snapshot used for the corresponding attempt and never repair unknown references.
12. Source granularity should support meaningful traceability while preserving enough semantic context for interpretation.

## Source Construction

Project Inputs may include text, structured data, files, images, and later other input forms. Core Input Processing may transform large inputs into bounded evidence fragments before constructing the Source Context.

The agent does not control chunking, extraction, storage locators, or source-reference assignment.

Structured customer input may preserve its supplied structure when this improves interpretation without adding information.

Core may later expose fragments of validated canonical artifacts as source items. The agent still receives only opaque source references and must not create artifact paths, JSON pointers, storage identifiers, or version locators.

## Retry Semantics

Each AI attempt is a distinct AgentExecution.

A workflow retry may use the same immutable evidence snapshot and add structured validation feedback as retry context.

If new customer evidence is introduced, the platform must create a new evidence snapshot and start a new requirements-analysis cycle rather than mutating the evidence basis of an existing attempt.

## Deliberately Not Frozen Here

V1 does not freeze:
- exact SourceContext JSON/DTO shape;
- source-reference regex;
- chunk size or chunking algorithm;
- OCR implementation;
- storage backend;
- hash algorithm;
- fragment locator representation;
- trust-level system;
- source-priority policy;
- exact metadata fields.

Those are Core implementation decisions and must preserve the invariants above.
