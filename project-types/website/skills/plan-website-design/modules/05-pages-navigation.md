# Pages and Navigation

## Purpose

Translate the information architecture of each proposal into concrete
pages, public routes, navigation groups, and navigation targets.

Choose page and navigation structures according to the proposal direction,
canonical requirements, relevant customer information, and the established
information hierarchy.

Do not create pages or navigation items mechanically from requirement types.

## Page Planning

Create a page when the information or interaction has a meaningful
independent purpose, sufficient structural value, or an explicit
customer constraint requires independent access.

Do not create thin or redundant pages merely to increase page count.

Do not force all content onto a single page when separation would better
serve the information architecture.

Each page must have:

- a unique localRef;
- a meaningful name;
- a planned public route;
- a concise purpose;
- at least one planned section in the final website plan.

## Structural Models

Support one-page, multi-page, and hybrid structures.

Select the model that best expresses each proposal's information architecture
and design direction within the permitted design space.

Different proposals may use different structural models and page counts.

## Root Page

Plan exactly one logical root page with the route `/` in each proposal.

Treat this route as relative to the website's deployment root.

Do not encode hosting paths, preview URLs, framework routing configuration,
or deployment infrastructure in page routes.

## Routes

Treat public routes as information-architecture decisions.

Use concise, meaningful route paths appropriate to the page purpose.

Do not encode framework syntax, file paths, infrastructure URLs, or
implementation-specific routing details.

Routes must be unique within the proposal.

Do not silently normalize or repair route values during design output.

## Navigation Planning

Do not derive navigation automatically from the page list.

Create only the navigation groups that meaningfully support the proposal.

Available navigation group roles include primary, footer, utility, and custom.

A page does not need to appear in primary navigation merely because it exists.

Keep primary navigation focused according to the proposal's information hierarchy.

## Navigation Labels

Use concise interface labels that accurately represent their destinations.

Navigation labels are designer-created interface decisions unless explicitly
constrained by customer input.

Do not introduce unsupported factual, promotional, ranking, or performance
claims through navigation labels.

## Internal Page Targets

Use page targets when a navigation item or action leads to another planned page.

Reference the page by its localRef within the same proposal.

Do not create references to pages from another proposal.

## Internal Section Targets

Use section targets when navigation or an action leads to a planned section,
including one-page and hybrid structures.

Reference both the containing page and the target section.

Ensure the section belongs to the referenced page.

Do not encode implementation-specific anchor IDs.

## Canonical External Targets

Use customer-data targets only when the referenced canonical customer-profile
fragment itself represents a valid actionable destination.

Do not create, copy, repair, or infer external destinations when a canonical
reference is available.

Do not treat arbitrary customer data as a navigable target.

## Requirement Targets

Use requirement targets only when the referenced canonical requirement itself
defines an actionable destination or integration target.

Do not use a requirement reference as a substitute for an unresolved page,
section, or interaction destination.

If the requirement does not determine a destination, plan the appropriate
page or section target instead.

## Mandatory Accessibility of Scope

Ensure all mandatory requirements remain meaningfully reachable through
the planned page, navigation, or action structure.

Do not achieve proposal differentiation by making required content or
functionality difficult to access.

Optional requirements may be exposed differently where their supplied
strength permits.

## Legal and Conventional Pages

Do not create pages solely from assumed legal, geographic, industry, or
common-practice requirements.

Only plan such pages when supported by canonical requirements, customer
information, or explicitly authorized platform context.

## Multilingual Considerations

Plan a logical page and navigation structure that can support the canonical
language requirements.

Do not invent localized route structures or complete translations unless
they are explicitly part of the authoritative input contract.

Localization-specific visual and interaction behavior is handled in the
responsive and localization design phase.

## Cross-Proposal Variation

Allow proposals to differ substantially in page count, structural model,
route organization, and navigation presentation where the permitted design
space allows.

Preserve the same mandatory requirement scope across all proposals.

## Consistency Check

Before continuing, confirm for each proposal that:

- exactly one logical root page exists;
- planned routes are unique;
- every page has a meaningful purpose;
- navigation does not mechanically duplicate the page list;
- all page and section targets are internally coherent;
- external targets rely only on appropriate canonical references;
- mandatory content and functionality remain reachable;
- no unsupported pages or destinations were invented.

## Handoff

After finalizing pages, routes, and navigation structure for each proposal,
continue to Sections and Elements Planning.

Translate each page purpose and information grouping into concrete ordered
sections and implementation-ready semantic elements.
