# Requirements Agent V1 Handoff

This package contains the frozen Website Requirements Agent V1 specification and the minimum Core contracts required to implement it.

Copy the contained `project-types/` and `docs/core/` paths into the corresponding locations of the AI Build Platform repository.

## Contents

- Requirements Agent configuration and role instructions
- Customer Profile JSON Schema
- Website Requirements JSON Schema
- Requirements extraction skill and 13 ordered modules
- Requirements Integrity rule
- Source Context / Source Reference Core contract
- Runner / Validation Core contract
- Candidate / Artifact Persistence Core contract
- V1 freeze note

## Important

Do not ask a coding agent to redesign these semantics during implementation. If an implementation detail is deliberately left open, choose the smallest project-type-agnostic implementation that preserves the contracts.

The Skill Loader must actually include the module contents in the active skill context. Merely storing Markdown links without loading their content is not sufficient.

Relational rules intentionally left out of JSON Schema must be enforced by deterministic validators where specified by the contracts.
