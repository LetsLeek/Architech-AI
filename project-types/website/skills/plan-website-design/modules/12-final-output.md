# Final Output

## Purpose

Finalize the current Designer Agent candidate and emit only the structured
design-proposal-set required by the agent output contract.

Do not introduce new design scope during finalization.

Do not perform workflow, approval, development, QA, persistence, or deployment work.

## Output Contract

Produce exactly one design-proposal-set candidate.

The proposal set must contain exactly three complete design proposals as required
by the current Designer Agent and output schema contracts.

Do not produce additional artifacts, notes, summaries, recommendation documents,
developer instructions, preview information, or free-form commentary.

## Proposal Completeness

Each proposal must be independently complete.

Do not use shorthand such as "same as proposal A except..." or rely on another
proposal for missing website-plan or design-specification content.

Each proposal must contain its own complete structured website plan and design
specification.

## Equal Proposal Status

Treat all proposals as equal customer-facing alternatives.

Do not mark a proposal as recommended, preferred, default, winning, experimental,
secondary, or intentionally weaker.

Array order does not represent ranking.

## Candidate Status

The emitted output is a candidate artifact.

Do not claim that it is validated, approved, canonical, persisted, selected,
production-ready, or deployed.

Validation, artifact registration, persistence, retry, review, and failure policy
belong to the Runner and Workflow.

## Schema Discipline

Emit only fields permitted by the design-proposal-set schema.

Do not add rationale, scoring, confidence, recommendations, implementation hints,
validation results, internal reasoning, or other undeclared properties.

Respect required and optional fields exactly as defined by the output contract.

## Local Reference Check

Before emitting the candidate, verify internally that all generated localRef values
are unique within the entire design-proposal-set artifact.

Check proposal, page, section, element, navigation-group, and UI-pattern references.

Local references remain non-persistent identifiers.

## Internal Reference Check

Verify internally that:

- every pageRef points to an existing page in the same proposal;
- every sectionRef points to an existing section under the referenced page;
- every patternRef points to an existing UI pattern in the same proposal;
- CTA targets are complete and coherent;
- no local references cross proposal boundaries;
- no dangling local references remain.

Do not emit a separate validation report.

## Canonical Reference Check

Use only platform-provided canonical references available to the current execution.

Verify that requirementRefs and customerDataRefs have not been invented,
renamed, repaired, or reinterpreted.

Do not substitute original sourceRefs or prior candidate localRefs for canonical references.

Do not fabricate a missing reference merely to make the output appear complete.

## Upstream Preservation

Do not rewrite, normalize, correct, enrich, or resolve customer-profile or
website-requirements content during finalization.

Do not introduce new customer facts, requirements, features, claims, translations,
or conflict resolutions to fill perceived gaps.

## Design Scope Check

Confirm that all planned content and functionality remain within the authoritative
customer and workflow scope.

Do not add conventional pages, forms, integrations, legal requirements, external
destinations, or functionality solely because they appear useful or common.

## Implementation Boundary

Do not add React components, framework decisions, CSS, Tailwind classes, DOM trees,
file paths, APIs, database models, deployment settings, or other Developer Agent work.

The output must remain implementation-ready design specification rather than implementation.

## Final Proposal Review

Before emission, verify internally that:

- exactly three proposals are present;
- every proposal is complete and implementation-ready;
- every proposal remains internally coherent;
- all mandatory requirements remain represented;
- authoritative constraints remain preserved;
- meaningful proposal differentiation remains present where design freedom permits;
- customer facts retain their canonical meaning;
- unknowns and conflicts have not been silently resolved;
- traceability remains precise and domain-correct;
- baseline responsive behavior is complete;
- localization is included only when required;
- no proposal is ranked or recommended;
- no implementation-specific content has been introduced.

If the current candidate contains a correctable internal inconsistency, revise the
candidate within the same Designer Agent execution and repeat the final internal check.

Do not initiate additional AgentExecutions, workflow retries, or unbounded revision loops.

## Emission

Emit only the final structured design-proposal-set candidate required by the
Designer Agent output contract.

Do not wrap the artifact in explanatory prose or an additional response object unless
the Core output protocol explicitly requires such transport wrapping.
