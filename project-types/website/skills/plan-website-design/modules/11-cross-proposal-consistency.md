# Cross-Proposal Consistency

## Purpose

Verify that the three distinct design proposals remain grounded in the same
canonical customer information, website requirements, authoritative constraints,
and unresolved input state.

Proposal diversity must change design decisions, not customer truth or mandatory scope.

## Canonical Basis

Treat the same canonical customer-profile and website-requirements artifacts
as the authoritative basis for all three proposals.

Do not reinterpret, rewrite, correct, enrich, or replace upstream canonical content
on a per-proposal basis.

A design proposal may present the same canonical information differently,
but must not assign it a different factual meaning.

## Mandatory Requirement Consistency

Preserve every must requirement across all proposals.

Different proposals may satisfy a mandatory requirement through different pages,
sections, elements, navigation structures, or visual treatments.

Do not treat a mandatory requirement as optional in one proposal merely to increase variety.

## Should and Could Requirements

Should requirements should normally remain represented across all proposals,
subject to their canonical meaning and permitted design flexibility.

Could requirements may be included, emphasized, reduced, or omitted differently
where their supplied optional strength permits.

Do not change the supplied requirement strength within a proposal.

## Constraint Consistency

Apply authoritative customer and workflow constraints consistently across all proposals.

Do not weaken, reinterpret, or selectively ignore a constraint in one proposal
to create greater differentiation.

Design freedom exists only inside the remaining permitted space.

## Customer Fact Consistency

Use canonical customer facts consistently across proposals.

The same canonical fragment must retain the same factual meaning wherever it is used.

Different placement, hierarchy, visual treatment, or omission of optional information
does not change the underlying fact.

Do not introduce proposal-specific business facts.

## Claims

Preserve the supplied epistemic status of provided claims.

Do not transform a customer-provided promotional, comparative, ranking,
performance, or achievement claim into an independently verified fact in any proposal.

Different proposals may present an allowed claim differently without changing its meaning.

## Unknowns

Keep missing and ambiguous information unresolved across all proposals.

Do not invent different factual assumptions for different design alternatives.

Proposals may use different flexible design strategies to accommodate unresolved content,
but must not turn uncertainty into proposal-specific facts.

## Conflicts

Do not use proposal variation as a mechanism for resolving canonical conflicts.

If canonical input preserves conflicting information, all proposals must preserve
the unresolved status rather than selecting different sides of the conflict.

Do not resolve conflicts by majority, plausibility, recency, design preference,
or customer-preview choice.

## Reference Domains

Use requirementRefs only for valid canonical website-requirements fragments.

Use customerDataRefs only for valid canonical customer-profile fragments.

Treat canonical references as opaque and preserve their meaning across proposals.

Do not mix reference domains or reinterpret references based on identifier format.

## Local Reference Isolation

All localRef values belong to the current design-proposal-set artifact and are
not persistent platform identifiers.

Keep page, section, element, navigation, and UI-pattern relationships inside
the proposal in which they are defined.

Do not create cross-proposal pageRef, sectionRef, patternRef, or equivalent
local-reference dependencies during initial proposal generation.

Cross-proposal composition occurs only through later approved revision workflows.

## Structural Variation

Allow proposals to differ in page count, routes, navigation, section organization,
content emphasis, visual system, and interaction design where permitted.

Do not confuse structural variation with scope variation.

The same mandatory customer intent must remain implementable and reachable
in every proposal.

## Localization and Responsive Consistency

Apply canonical language requirements consistently across all proposals.

When multilingual or RTL behavior is required, every proposal must support it,
although the interaction and presentation design may differ.

All proposals must include the platform-required baseline responsive design behavior.

Do not use lack of responsive or localization completeness as a differentiation strategy.

## Upstream Preservation

Do not silently correct upstream customer-profile or website-requirements artifacts.

Do not normalize, rewrite, enrich, or reinterpret upstream data because an alternative
value would be more convenient for the design.

If an upstream issue prevents valid design planning, preserve the issue in the candidate
context and allow validation or workflow policy to determine the next action.

## Equal Contract Quality

Ensure all three proposals are complete to the same output-contract standard.

Do not leave one proposal less detailed, less traceable, less responsive,
or less implementation-ready than the others.

## Cross-Proposal Review

Before finalization, compare all three proposals against the canonical inputs.

Confirm that:

- all must requirements remain represented;
- requirement strengths have not been reclassified;
- authoritative constraints retain the same meaning;
- customer facts retain the same meaning;
- provided claims retain their supplied status;
- unknowns and conflicts remain unresolved where required;
- canonical reference domains are correct;
- no proposal contains invented business facts or unsupported functional scope;
- local references do not create cross-proposal dependencies;
- required languages and baseline responsive behavior are supported across all proposals;
- all proposals meet the same implementation-readiness standard.

If inconsistencies were introduced during design development, correct the current
candidate proposals within the same Designer Agent execution before final output.

Do not initiate workflow retries or additional AgentExecutions.

## Handoff

After confirming cross-proposal consistency, continue to Final Output Preparation.

Perform the final artifact-level consistency checks and emit only the required
design-proposal-set candidate.
