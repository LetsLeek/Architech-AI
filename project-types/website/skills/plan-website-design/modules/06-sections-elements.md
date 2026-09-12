# Sections and Elements

## Purpose

Translate each planned page into an ordered set of coherent sections and
implementation-ready semantic elements.

Define enough visible structure for a Developer Agent to implement the proposal
without inventing major layout, content-placement, interaction, or presentation
decisions.

Do not create a DOM tree, component tree, CSS specification, or framework-specific
implementation plan.

## Section Planning

Create sections as meaningful presentation units with a coherent purpose.

Do not create separate sections for trivial individual elements.

Do not combine unrelated information into overly broad sections that leave the
Developer Agent to determine the actual composition.

The order of sections in the page plan represents their intended presentation order.

## Section Identity and Type

Give each section a unique localRef within the artifact.

Use the most appropriate known section kind when one exists.

Use custom only when the section does not fit an existing semantic type, and provide
a clear customKind.

Do not force a custom section into an inaccurate standard category.

## Section Purpose

Describe the role each section serves within the page and information hierarchy.

The purpose should explain what the section accomplishes rather than merely repeat
its name or kind.

## Requirement References

Attach requirementRefs only when the section or element materially supports the
referenced canonical requirement.

A requirement reference means that the design decision contributes to satisfying
the requirement. It does not mean the customer requested that exact section,
element, layout, or UI pattern.

Do not fabricate requirement references for pure designer decisions.

Place references at the most specific meaningful level and avoid unnecessary
duplication across section and element levels.

## Customer Data References

Use customerDataRefs when the section or element is intended to present or use
specific canonical customer-profile information.

Reference only data that is actually relevant to the planned content.

Do not duplicate canonical factual content into free-form design text when a
reference can preserve the source of truth.

Do not infer additional facts from referenced customer data.

## Section Layout Intent

Define an implementation-independent layoutIntent for every section.

Describe composition, hierarchy, relative grouping, and major visual relationships
clearly enough to guide implementation.

Concrete design decisions such as split layouts, card groupings, dominant media,
or multi-column composition are allowed.

Do not specify CSS properties, framework classes, DOM structure, file paths, or
implementation-specific layout syntax.

## Section-Specific Responsive Behavior

Specify responsiveBehavior when a section requires meaningful behavior beyond the
proposal's global responsive principles.

Describe how composition, ordering, emphasis, or media treatment should adapt.

Do not prescribe concrete CSS media queries or framework breakpoints.

## Element Planning

Represent the major semantic elements needed to realize each section.

Elements may include headings, text, imagery, actions, card groups, lists, galleries,
forms, maps, social links, video, downloads, dividers, or custom semantic elements.

Elements are design units, not DOM nodes.

Keep element structure flat. Do not create recursive child element trees.

## Element Identity

Give every planned element a unique localRef within the artifact.

Use element references to support implementation, later review, and customer-driven
cross-proposal revision.

Local references are not persistent platform identifiers.

## Element Kind and Role

Use kind to describe the general element category.

Use role to describe the element's specific semantic function within its section.

Choose clear, meaningful roles that guide implementation without encoding technical
component names.

## Content Intent

Use contentIntent to describe what the element should communicate, display, or allow
the visitor to do.

Do not use contentIntent as a place to invent customer facts, marketing claims,
testimonials, achievements, or other unsupported content.

Do not generate large amounts of final copy as part of the design plan.

Interface-level microcopy may be implied or later implemented when it does not add
unsupported factual meaning.

## Functional Elements

Materialize functional requirements as concrete semantic elements where appropriate.

For example, a contact-form requirement should result in a planned form interaction,
not merely a vague statement that contact is supported.

Do not specify backend APIs, validation libraries, state management, data models,
or framework implementation.

## Action Targets

Every CTA element must have a meaningful target.

Use only valid page, section, customer-data, or requirement-based destinations
per the navigation target contract.

Do not leave the Developer Agent to invent the destination of a planned action.

## UI Pattern References

Use patternRef when an element should follow a reusable visual or interaction pattern
defined within the same proposal's design specification.

Do not reference patterns from another proposal.

Do not require a pattern reference for simple elements that do not need one.

## Media and Assets

Plan the role, placement, and desired character of media without inventing unavailable
customer assets.

Do not claim that a specific image, video, logo, or other asset exists unless it is
available through canonical input.

Asset sourcing, image generation, licensing, and external media retrieval are outside
this module's responsibility.

## Forms and Complex UI

Describe the semantic purpose and visible role of forms and other functional UI.

Honor explicitly supplied field or interaction requirements.

Do not build recursive form schemas, technical validation rules, or implementation
component trees into the design proposal.

## Repetition

Allow intentional reuse of canonical information when it improves discoverability
or serves a distinct presentation purpose.

Avoid uncontrolled duplication of the same content across sections.

Canonical customer data remains the source of truth wherever reused.

## Implementation-Readiness Check

Before continuing, confirm that each page has enough section and element detail for
a competent Developer Agent to implement the visible experience without inventing
major structural, interaction, or visual decisions.

Also confirm that:

- section and element purposes are coherent;
- mandatory content and functional requirements are visibly materialized;
- customer data references match intended usage;
- no unsupported customer facts or features were introduced;
- element plans remain semantic rather than implementation-specific;
- CTA destinations are concrete;
- cross-proposal references have not been introduced.

## Handoff

After finalizing sections and semantic elements for each proposal, continue to
Design Specification Planning.

Use the established proposal direction and website structure to define the shared
visual system, typography, color, spacing, layout principles, UI patterns, imagery,
motion, and responsive design language.
