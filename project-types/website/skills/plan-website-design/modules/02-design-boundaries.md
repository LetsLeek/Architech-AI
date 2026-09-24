# Design Boundaries

## Purpose

Determine the permitted design space before developing proposal directions.

Classify relevant design decisions as fixed, constrained, free, or unresolved
based on the canonical inputs and explicitly authorized workflow context.

This classification is internal working context and is not a separate output artifact.

## Fixed Decisions

Treat a design decision as fixed when authoritative input leaves no meaningful
choice about that decision.

Preserve the fixed decision across all proposals.

Do not expand a narrow fixed requirement into unrelated design restrictions.

A fixed requirement may still leave implementation and presentation choices free.

## Constrained Decisions

Treat a decision as constrained when authoritative input defines boundaries
or intent but still leaves meaningful design choices.

Develop alternatives only inside those boundaries.

Do not weaken constraints merely to make proposals appear more different.

## Free Decisions

Treat a decision as free when no authoritative input materially determines it.

Use free decisions as the primary design space for developing distinct,
coherent proposal directions.

Pure designer decisions do not require fabricated customer evidence or requirement references.

## Unresolved Decisions

Treat a decision as unresolved when required information is missing,
ambiguous, or conflicting.

Do not convert unresolved information into a definitive customer fact.

Where possible, plan flexible or neutral design behavior that can accommodate
later resolution.

Do not use different proposals as a mechanism for silently resolving factual conflicts.

## Requirement Strength

Do not equate requirement strength with design rigidity.

A must requirement is mandatory, but its presentation may remain free or constrained.

Should requirements should normally be respected across proposals.

Could requirements may be included differently across proposals when the customer
has explicitly expressed them as optional.

Do not invent additional optional features to create artificial differentiation.

## Goals and Audiences

Use goals and target audiences to constrain prioritization, hierarchy, and presentation
where supported.

Do not convert goals or audience information into new content, functional,
demographic, or business requirements.

## Customer Facts

Customer facts may inform content placement and reasonable design judgment.

Do not transform geography, industry, or general knowledge into unstated customer constraints.

## Proposal Differentiation Boundary

Create variation only within the permitted design space.

Customer requirements and authoritative constraints take precedence over proposal diversity.

When the available design freedom is narrow, prefer honest similarity over violating
or inventing requirements for the sake of difference.

## Handoff

After establishing fixed, constrained, free, and unresolved decisions,
continue to Proposal Direction Development.

Use the free and constrained design space to create distinct proposal directions
while preserving all fixed boundaries.
