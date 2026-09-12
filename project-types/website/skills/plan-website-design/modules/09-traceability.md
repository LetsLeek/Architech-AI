# Traceability

## Purpose

Maintain clear relationships between design decisions and the canonical
customer information or website requirements they materially use or satisfy.

Traceability must preserve the distinction between customer facts,
customer requirements, and independent designer decisions.

Do not fabricate evidence or references for creative design choices.

## Traceability Model

Use requirementRefs to reference canonical website-requirements fragments
that materially constrain, motivate, or are directly satisfied by a design decision.

Use customerDataRefs to reference canonical customer-profile fragments
that the planned design intends to display, use, or expose.

A pure designer decision may legitimately contain neither reference type.

The design proposal itself records the designer decision.

## Requirement References

A requirementRef means that the design decision contributes to satisfying
or respecting the referenced canonical requirement.

It does not mean that the customer explicitly requested the exact page,
section, layout, element, pattern, color, or other concrete design choice.

Do not reinterpret designer decisions as customer requirements.

## Customer Data References

A customerDataRef means that the planned design uses or presents the referenced
canonical customer information.

Do not treat the presence of a customer-data reference as evidence that the
customer requested the specific presentation choice.

Do not copy canonical factual values into free-form design text when a reference
can preserve the customer-profile as the source of truth.

## Reference Placement

Attach references at the most specific meaningful level.

Use page-level references only when the page itself is materially constrained
by the referenced requirement.

Use section-level references when the section as a whole fulfills the requirement.

Use element-level references when a specific element materially implements the
content or functional requirement.

Use design-specification-level or token-level references when the requirement
directly constrains the corresponding visual decision.

Avoid duplicating the same relationship across multiple levels unless each
placement carries distinct semantic meaning.

## Materiality

Reference only canonical requirements or customer data that materially relate
to the design decision.

Do not add every remotely related goal, audience, constraint, or customer fact
to every element.

Traceability should remain precise enough to support implementation, review,
QA, and later updates.

## Goals and Target Audiences

Goals and target audiences may be referenced when they materially influence
hierarchy, prioritization, or proposal-wide design behavior.

Do not use goal or audience references to create new functional or content
requirements.

Do not fabricate demographic, behavioral, market, or persona information.

## Constraints

Reference explicit design, navigation, content, business, integration, or
technical constraints where they materially restrict a design decision.

Preserve the difference between the customer's constraint and the designer's
chosen implementation of that constraint.

## Pure Designer Decisions

Do not fabricate requirementRefs or customerDataRefs for decisions made solely
within the permitted design freedom.

Examples may include:

- layout composition;
- section placement;
- typography choices;
- freely chosen color relationships;
- spacing;
- imagery treatment;
- UI pattern styling;
- motion character.

Unless an authoritative requirement constrains such a decision, the design
artifact itself is sufficient documentation of the decision.

## Canonical Reference Handling

Use only platform-provided canonical references available to the current execution.

Treat canonical references as opaque identifiers.

Do not create, rename, modify, repair, parse, or infer meaning from their format.

Do not use localRef values from prior candidate artifacts as canonical references.

Do not use original customer sourceRefs as a substitute for canonical artifact references.

## Artifact Boundaries

requirementRefs must reference valid canonical website-requirements fragments.

customerDataRefs must reference valid canonical customer-profile fragments.

Do not mix these reference domains.

Do not reference design fragments from another proposal through canonical input references.

## Invalid References

If a required canonical reference is unavailable or invalid, do not invent or
repair a replacement.

Do not silently drop or substitute references merely to make the candidate valid.

Validation and workflow policy determine whether the candidate is retried,
reviewed, or failed.

## Traceability and Reuse

When canonical customer data is intentionally reused in multiple parts of the
design, reuse the canonical reference rather than duplicating factual values.

Intentional reuse is allowed when it serves distinct presentation or
discoverability purposes.

## Traceability Check

Before continuing, confirm that:

- requirementRefs correspond only to materially relevant canonical requirements;
- customerDataRefs correspond only to customer information actually used;
- references are attached at the most meaningful level;
- the same relationship is not duplicated unnecessarily;
- pure designer decisions do not carry fabricated customer references;
- no sourceRefs or prior localRefs are used as canonical references;
- requirement and customer-data reference domains are not mixed;
- no canonical reference was invented, repaired, or reinterpreted.

## Handoff

After traceability is complete for all proposals, continue to Proposal
Differentiation Review.

Evaluate whether the three proposals remain meaningfully different while
preserving the same canonical requirement basis and all authoritative constraints.
