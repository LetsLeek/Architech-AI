# Responsive and Localization

## Purpose

Adapt each proposal's established structure and visual system for varying
viewport conditions and, when required by canonical website requirements,
multiple languages or text directions.

Responsive and localization planning modifies presentation behavior without
changing the underlying customer requirement scope.

Do not produce framework breakpoints, CSS media queries, translation files,
locale-routing implementation, or browser-specific code.

## Responsive Responsibility

Treat responsive behavior as a design responsibility rather than leaving
major layout and interaction decisions to the Developer Agent.

Define how hierarchy, composition, navigation, typography, spacing, media,
and important interactions adapt as available viewport space changes.

Do not create separate desktop and mobile products with different requirement scope.

## Viewport Model

Describe responsive behavior in implementation-independent terms such as
narrow, medium, and wide conditions where useful.

Do not prescribe concrete technical breakpoints unless an authoritative
constraint explicitly requires them.

The Developer Agent may choose implementation breakpoints that faithfully
realize the planned behavior.

## Navigation Behavior

Define how navigation remains understandable and usable as horizontal space
decreases.

A compact menu is one possible solution but is not mandatory.

Preserve access to mandatory pages, functions, language controls, and important
actions across viewport conditions.

Do not encode framework components or implementation-specific menu patterns.

## Content Stacking

Define how multi-column, split, card-based, and media-led compositions adapt
on narrower viewports.

Preserve semantic priority when content stacks or reorders.

Do not allow responsive transformation to obscure or remove mandatory
customer content or functionality.

## Typography Scaling

Define how the proposal's typographic hierarchy adapts as space decreases.

Preserve hierarchy and readability while allowing large display typography
and other scale-sensitive roles to reduce appropriately.

Do not create a complete breakpoint-specific typography token matrix in V1.

## Spacing Adjustment

Define how page gutters, section spacing, component density, and whitespace
adapt on narrower viewports.

Preserve the proposal's intended visual character while avoiding layouts that
depend on excessive desktop-scale spacing.

## Media Behavior

Define how imagery, video, galleries, and other media adapt to available space.

Preserve important content and focal intent.

Decorative media may be simplified or reduced when appropriate, but
requirement-relevant media or content must not silently disappear.

## Touch and Interaction

Ensure planned interactions remain understandable and usable in touch contexts.

Do not depend on hover as the only way to reveal essential content or actions.

Do not claim specific accessibility conformance before deterministic validation.

## Section-Specific Behavior

Use section.responsiveBehavior when a section needs behavior more specific
than the proposal-wide responsive principles.

Section-specific behavior may refine global rules but must not create
unresolved contradictions within the proposal.

## Localization Trigger

Create localization-specific design behavior only when supported by canonical
website language requirements or other explicitly authorized authoritative context.

Do not infer website languages from geography, customer-profile languages,
input language, industry, or general knowledge.

## Localization Responsibility

Plan the interface and layout implications of localization.

Do not generate or claim complete translations as part of the Designer Agent's
responsibility.

Do not replace canonical customer content with designer-authored translated facts
or marketing claims.

## Language Switcher

When multiple website languages require user selection, define how language
selection remains discoverable and accessible across viewport conditions.

Do not assume a specific flag-, text-, dropdown-, or menu-based representation
unless chosen as part of the proposal design.

Do not treat country flags as a required representation of language.

## Text Expansion

Plan navigation, buttons, cards, headings, and other constrained UI so that
reasonable variation in translated text length does not break the intended hierarchy
or obscure essential meaning.

Do not rely on one language's current text length as the only valid layout case.

## Directionality

When canonical language requirements include right-to-left presentation,
define how reading order, alignment, navigation, directional relationships,
and relevant layout composition adapt.

Do not reduce RTL support to text alignment alone.

Do not introduce RTL-specific scope when no authoritative requirement makes it relevant.

## Localization References

Localization-specific specification must reference the canonical language or
related requirements that require the behavior.

Do not fabricate requirement references for speculative multilingual support.

## Scope Preservation

Responsive and localized forms of a proposal must preserve mandatory customer
content, functions, and authoritative constraints.

Presentation may adapt substantially; underlying customer scope must not
silently change by viewport or language.

## Implementation Readiness Check

Before continuing, confirm for each proposal that:

- global navigation adaptation is defined;
- major content stacking behavior is clear;
- typography and spacing adaptation preserve hierarchy;
- media behavior is defined;
- essential interaction does not depend solely on hover;
- mandatory content and functionality remain available across viewport conditions;
- section-specific responsive rules do not contradict global behavior;
- localization appears only when authoritative input requires it;
- language selection is planned when multiple languages require it;
- text expansion is accommodated;
- RTL behavior is addressed when relevant;
- no complete translations, CSS breakpoints, framework code, or browser-specific implementation details were introduced.

## Handoff

After finalizing responsive and localization behavior for each proposal,
continue to Traceability Planning.

Attach canonical requirement and customer-data references at the most meaningful
levels without fabricating evidence for independent designer decisions.
