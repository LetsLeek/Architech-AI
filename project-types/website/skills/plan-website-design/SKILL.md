# Plan Website Design

## Purpose

Transform validated canonical customer information and website requirements into a coherent `design-proposal-set` containing three distinct, complete, and implementation-ready website design proposals.

The skill defines the design-planning process used by the Website Designer Agent.

It does not implement websites, validate canonical persistence, select a preferred proposal, perform customer approval, or control workflow retries.

## Operating Model

This skill executes within a single Designer Agent execution.

The three design proposals are developed within that same execution and emitted together as one candidate `design-proposal-set`.

The modules belonging to this skill are instruction modules within one skill.

They are not:

- separate AI calls;
- separate AgentExecutions;
- workflow steps;
- retry attempts;
- independently persisted artifacts.

Do not initiate additional AI executions or unbounded design loops from within this skill.

Workflow policy determines whether a completed candidate is retried, reviewed, accepted, or failed after Runner validation.

## Canonical Inputs

The Designer Agent operates on validated canonical inputs supplied to the current execution, including:

- `customer-profile`;
- `website-requirements`.

Additional workflow-provided context may constrain the current operation when explicitly authorized by the platform.

Treat canonical input artifacts as authoritative for their respective domains.

Do not rewrite, repair, enrich, replace, or silently correct them.

Canonical inputs may contain unknowns, ambiguities, conflicts, or customer-provided claims. Validation does not mean that every fact is complete, conflict-free, or independently verified.

## Core Boundary

Preserve customer truth and requirement scope while making independent design decisions inside the remaining permitted design space.

Distinguish throughout the design process between:

- canonical customer facts;
- canonical customer requirements;
- authoritative constraints;
- unresolved information;
- independent designer decisions.

Do not convert designer decisions into customer requirements.

Do not invent customer facts, requirements, features, claims, destinations, translations, or evidence merely because they would make a design more convenient or complete.

Independent designer decisions do not require fabricated customer evidence.

## Design Responsibility

The Designer Agent decides the visible and experiential design of each proposal, including where appropriate:

- information hierarchy;
- information architecture;
- page structure;
- public routes;
- navigation structure;
- section organization;
- semantic UI elements;
- layout intent;
- visual direction;
- colors;
- typography;
- spacing;
- layout system;
- reusable UI patterns;
- imagery treatment;
- motion direction;
- responsive behavior;
- localization-related design behavior.

The Designer Agent does not decide implementation architecture.

Do not specify:

- React component architecture;
- TypeScript implementation;
- DOM trees;
- CSS or Tailwind classes;
- backend architecture;
- APIs;
- database models;
- source file structure;
- deployment infrastructure;
- Git strategy;
- QA results.

A proposal is implementation-ready when a competent Developer Agent can implement the visible experience without inventing major structural, visual, or interaction decisions.

Implementation-ready does not mean pixel-level or framework-level specification.

---

# Phase A — Input & Design Boundary Analysis

Establish the authoritative input state and determine the permitted design space before creating proposals.

Follow:

1. `01-input-contract.md`
2. `02-design-boundaries.md`

## Phase Goal

Understand:

- what canonical customer information is available;
- what the website requirements mean;
- which constraints are authoritative;
- which decisions are fixed;
- which decisions are constrained;
- which decisions remain free;
- which decisions remain unresolved.

Do not begin detailed proposal design before these boundaries are understood.

Requirement strength and design rigidity are not the same concept.

A mandatory requirement may still leave substantial freedom in its visual and structural implementation.

Unknowns and conflicts remain unresolved unless authoritative upstream information resolves them.

Do not use design alternatives as a mechanism for resolving factual uncertainty.

---

# Phase B — Proposal Design

Develop three coherent design directions and expand each into a complete website design proposal.

Follow:

3. `03-design-directions.md`
4. `04-information-architecture.md`
5. `05-pages-navigation.md`
6. `06-sections-elements.md`
7. `07-design-specification.md`
8. `08-responsive-localization.md`

## Proposal Direction Development

Establish three distinct design directions before fully detailing them.

Each direction must be:

- coherent;
- professionally viable;
- compatible with the same canonical input basis;
- implementable within the project scope;
- meaningfully distinct where design freedom permits.

Treat all three as equal customer-facing alternatives.

Do not create a deliberately weak, filler, throwaway, or intentionally inferior proposal.

Do not rank or recommend one proposal.

## Diverge Before Detailing

First establish distinct proposal identities.

Then expand each direction into its own complete:

- information architecture;
- page and navigation structure;
- section and element plan;
- visual design specification;
- responsive behavior;
- localization behavior where required.

Do not create one proposal and then clone it with minor cosmetic changes.

Do not create differentiation by changing mandatory requirement scope or inventing unsupported functionality.

## Information Architecture

Organize canonical requirements and relevant customer information into meaningful information relationships before converting them into concrete pages.

Do not assume:

`requirement = page`.

A requirement may be represented by:

- a page;
- a section;
- an element;
- a navigation relationship;
- a functional interaction;
- another appropriate design structure.

Group and prioritize information according to customer goals, target audiences, requirements, constraints, available customer data, and the current proposal direction.

Do not create new business facts through presentation grouping.

## Pages and Navigation

Translate each proposal's information architecture into a concrete public structure.

Support:

- one-page;
- multi-page;
- hybrid

website structures where appropriate.

Different proposals may use different page counts and navigation models.

Every proposal must have exactly one logical root page at `/`.

Do not mechanically place every page in primary navigation.

Do not automatically create conventional pages merely because websites commonly contain them.

Public routes are information-architecture decisions, not implementation router configuration.

## Sections and Elements

Translate every page into ordered semantic sections and major visible elements.

Provide enough structure that the Developer Agent does not need to invent:

- major section ordering;
- major visible content groupings;
- core actions;
- functional UI presence;
- major layout composition;
- major reusable presentation patterns.

Keep semantic element structures flat.

Do not create a serialized DOM or recursive component tree.

## Design Specification

Define one coherent visual system for each proposal.

Include the required proposal-level design decisions for:

- colors;
- typography;
- spacing;
- layout;
- UI patterns;
- imagery;
- responsive behavior;
- optional motion;
- localization behavior where required.

Use concrete design values where the Developer Agent would otherwise need to make a major visual decision.

Do not translate those decisions into framework-specific implementation.

## Responsive Design

Responsive behavior is part of the design responsibility.

Do not leave major mobile or narrow-screen UX decisions unspecified for the Developer Agent.

Define how:

- navigation adapts;
- content stacks;
- hierarchy is preserved;
- typography scales;
- spacing changes;
- media adapts;
- important interactions remain reachable.

Do not require fixed technical breakpoint values unless authoritative input explicitly constrains them.

Mandatory content and functionality must not silently disappear on smaller viewports.

## Localization

Plan localization-specific interface behavior only when authoritative website requirements make it relevant.

Do not infer website languages from:

- geography;
- customer-profile language data;
- language of the original customer input;
- industry;
- general knowledge.

The Designer Agent plans localization-aware layout and interaction.

It does not become the full translation or copywriting system.

---

# Phase C — Traceability & Proposal Review

After fully developing the proposals, establish precise traceability and review the proposal set both for meaningful difference and canonical consistency.

Follow:

9. `09-traceability.md`
10. `10-proposal-differentiation.md`
11. `11-cross-proposal-consistency.md`

## Traceability

Use:

- `requirementRefs` for materially relevant canonical website-requirements fragments;
- `customerDataRefs` for canonical customer-profile information materially used by the design.

Treat canonical references as opaque platform-provided identifiers.

Do not:

- invent them;
- repair them;
- rename them;
- parse meaning from their format;
- substitute previous candidate `localRef` values;
- substitute original evidence `sourceRefs`.

Attach references at the most specific meaningful level.

Avoid unnecessary reference duplication.

A pure designer decision may legitimately have no customer or requirement reference.

Do not fabricate traceability merely to make creative decisions appear customer-derived.

## Proposal Differentiation Review

Compare the fully developed proposals together.

Confirm that they remain meaningfully distinct where design freedom permits.

Relevant dimensions may include:

- information architecture;
- page structure;
- content emphasis;
- navigation;
- section composition;
- visual hierarchy;
- typography;
- color usage;
- spacing and density;
- imagery;
- UI pattern language;
- motion;
- responsive composition.

Do not use trivial token changes or naming differences as the sole basis for proposal differentiation.

Do not require arbitrary numeric difference thresholds.

Authoritative constraints always take precedence over differentiation.

Where little design freedom remains, prefer honest similarity over constraint violation.

## Cross-Proposal Consistency

Verify that all three proposals remain based on the same canonical customer truth and requirement semantics.

They may differ in design.

They must not differ in:

- mandatory customer scope;
- meaning of customer facts;
- supplied requirement strength;
- authoritative constraint meaning;
- unresolved factual state;
- epistemic status of provided claims.

Do not use proposals as competing factual interpretations.

A conflict in canonical input remains a conflict across all proposals.

A missing fact remains missing across all proposals.

A provided claim remains customer-provided rather than independently verified.

All three proposals must meet the same implementation-readiness standard.

---

# Phase D — Finalization

Follow:

12. `12-final-output.md`

No new design scope should be introduced during this phase.

## Final Candidate Review

Before emission, internally verify that:

- exactly three proposals are present;
- every proposal is independently complete;
- every proposal is implementation-ready;
- all mandatory requirements remain represented;
- authoritative constraints remain preserved;
- proposal differentiation remains meaningful where permitted;
- all three proposals remain grounded in the same canonical input basis;
- customer facts retain their canonical meaning;
- provided claims retain their supplied status;
- unknowns and conflicts have not been silently resolved;
- requirement and customer-data references remain domain-correct;
- generated `localRef` values are unique within the artifact;
- internal page, section, target, and pattern references are coherent;
- responsive behavior is sufficiently specified;
- localization exists only when required;
- no proposal is ranked or recommended;
- no unsupported functionality or customer facts were introduced;
- no Developer Agent implementation work has leaked into the design artifact.

Correct internally detectable candidate inconsistencies within the same Designer Agent execution before emission.

Do not start another AgentExecution or workflow retry.

## Output Contract

Emit exactly one candidate artifact:

`design-proposal-set`

The candidate must conform to the current `design-proposal-set` schema.

Do not emit additional:

- explanations;
- recommendations;
- design summaries outside the artifact;
- customer-facing ranking;
- developer notes;
- QA reports;
- preview information;
- persistence status;
- approval status.

Each proposal must be fully represented in the artifact.

Do not use shorthand such as:

- "same as proposal A";
- "same structure with different colors";
- "reuse proposal B except for...".

The output is a candidate only.

Do not claim that it is:

- validated;
- canonical;
- persisted;
- approved;
- selected;
- deployed.

Those states belong to Runner, Validator, Artifact Persistence, Workflow, and Human Approval responsibilities.

## Completion

The skill is complete when one structured `design-proposal-set` candidate containing three coherent, distinct, traceable, implementation-ready website design proposals has been emitted for Runner validation.
