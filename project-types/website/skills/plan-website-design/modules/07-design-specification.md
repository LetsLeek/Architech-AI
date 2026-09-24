# Design Specification

## Purpose

Define the coherent visual design system for each proposal after its pages,
sections, elements, and structural intent have been established.

Provide enough concrete visual direction for a Developer Agent to implement
the proposal without inventing major styling or presentation decisions.

Do not produce CSS, framework configuration, implementation components,
or a serialized visual-design canvas.

## Proposal-Wide Coherence

Treat the design specification as the shared visual language of the proposal.

Derive concrete visual decisions from the established design direction and
website structure.

Keep colors, typography, spacing, layout principles, UI patterns, imagery,
and optional motion mutually coherent.

Do not allow sections to become unrelated visual systems unless authoritative
input explicitly requires such variation.

## Color System

Define a concise role-based set of concrete implementation-ready colors.

Use semantic roles such as background, surface, text, primary, accent, or border
rather than arbitrary token names.

Respect authoritative customer brand and design constraints.

Do not modify required customer colors for aesthetic convenience.

Do not represent freely chosen designer colors as customer brand facts.

Choose color relationships that support clear visual hierarchy and reasonable
contrast, but do not claim accessibility conformance before validation.

## Typography

Define role-based typography sufficient to establish a clear hierarchy.

Specify concrete font families, weights, sizes, line heights, and other supplied
typographic properties where relevant.

Select typography consistent with the proposal direction and customer constraints.

Do not make unsupported claims about font licensing, ownership, or availability.

Do not encode CSS classes, framework typography utilities, or implementation components.

## Spacing

Define a coherent role-based spacing scale using concrete values.

Use spacing to express the intended proposal density and hierarchy.

Avoid arbitrary values that do not form a usable visual system.

Do not encode framework spacing classes or implementation-specific token names.

## Layout System

Define the proposal-wide content-width intent, density, gutters, section spacing,
and grid character.

Use layout decisions to support the established proposal direction.

Section-specific composition remains defined by section layoutIntent.

Do not translate layout decisions into CSS grid syntax, framework classes,
DOM structures, or absolute implementation coordinates.

## UI Patterns

Define reusable visual and interaction patterns when elements across the proposal
share a common treatment.

Give each pattern a unique localRef within the artifact.

Use a semantic pattern kind and concise description.

Describe visualTreatment clearly enough to guide implementation without specifying CSS.

Use interactionBehavior where a reusable interaction treatment is materially relevant.

Patterns are design abstractions, not React components or implementation modules.

## Pattern Scope

UI patterns belong to exactly one proposal.

Elements may reference patterns only within the same proposal.

Do not reference patterns across proposals during initial design generation.

Cross-proposal combinations are handled through later customer-approved revision workflows.

## Imagery

Define the intended visual role, character, and treatment of imagery.

Describe photography, illustration, media framing, cropping, or icon direction
when relevant to the proposal.

Do not invent unavailable customer assets.

Do not claim that specific photos, logos, videos, or illustrations exist unless
supported by canonical input.

Asset sourcing, licensing, external search, and image generation are outside this skill.

## Motion

Define motion only when it contributes meaningfully to the proposal.

Keep motion proportional to the project scope and design direction.

Describe intensity, overall style, and reduced-motion behavior.

Do not specify animation libraries, framework APIs, implementation timelines,
or technical motion code.

Do not create unsupported feature scope through elaborate motion concepts.

## Requirement-Driven Design Decisions

Attach requirementRefs to design tokens or specification areas only when an
authoritative requirement materially constrains that decision.

For example, an explicitly required brand color or font may be referenced.

Do not fabricate requirement references for freely chosen designer decisions.

## Constraint Preservation

Customer design constraints take precedence over visual novelty.

When all proposals share a required brand property, preserve it consistently
and differentiate through other permitted design dimensions.

Do not violate constraints merely to make proposals appear more different.

## Implementation Readiness

Ensure the design specification is concrete enough that a competent Developer Agent
does not need to choose the fundamental palette, typography hierarchy, spacing system,
layout character, recurring UI treatments, or imagery direction.

Implementation readiness does not require pixel-level coordinates, CSS properties,
component code, or framework configuration.

## Internal Consistency Check

Before continuing, confirm that:

- the color system supports the proposal concept;
- typography establishes a coherent hierarchy;
- spacing reflects the intended density;
- layout principles fit the planned pages and sections;
- UI patterns form a consistent visual language;
- imagery treatment supports the same direction;
- optional motion remains appropriate to scope;
- customer design constraints remain preserved;
- freely chosen design decisions are not misrepresented as customer requirements;
- no implementation-specific code or framework decisions were introduced.

## Handoff

After establishing a complete visual design system for each proposal,
continue to Responsive and Localization Planning.

Adapt the visual and structural system for varying viewport sizes and,
where required, multiple languages or text directions without changing
the underlying customer requirement scope.
