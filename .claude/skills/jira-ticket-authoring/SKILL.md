---
name: jira-ticket-authoring
description: Turns an approved requirement or problem statement into a well-structured Jira ticket in the AIW project - searches for duplicates and a fitting parent Epic first, and only creates a new issue once that context is resolved. Use this whenever new work needs to be tracked in Jira, not as part of the per-ticket implementation chain (jira-ticket-intake through human-review-publish).
---

# Jira Ticket Authoring

## Purpose

Turn a requested requirement or problem into a high-quality Jira work item - without creating a
duplicate of something that already exists, and without misclassifying it under the wrong Epic.
This is the inverse of `jira-ticket-intake` (AIW-60): that skill reads an existing ticket to start
work; this one writes a new one (or updates an existing one) once new work has been identified.

## When to use

Whenever a new requirement, bug, or piece of work needs to become trackable Jira state in the
`AIW` project - not as a step inside the per-ticket implementation chain
(`jira-ticket-intake` → `repository-investigation` → ... → `human-review-publish`), which already
assumes its ticket exists. Typical triggers: a human describes new work in conversation and asks
for it to be tracked; `implementation-verification` or `acceptance-criteria-verification` surface
a real gap that deserves its own ticket rather than silently expanding the current one's scope.

## Inputs

- `requirement` (string, required): the requested work or problem, in the requester's own words -
  not yet normalized or structured.
- `knownContext` (object, optional): anything already known that shouldn't be re-derived - a
  suspected parent Epic, a target milestone/fix version, related ticket keys the requester already
  mentioned. Treated as a hint to verify, never as ground truth to skip verification on (see Steps).

## Preconditions

- Jira MCP tools (`mcp__atlassian__*`) are available and authenticated for this session.
- The requirement is concrete enough to search and classify - a vague idea ("we should improve
  performance sometime") is not yet ready for this skill; it needs to be narrowed with the
  requester first (see Stop conditions).

## Steps

1. **Normalize** the requirement into a short, unambiguous summary line and a one-paragraph
   problem/goal statement, grounded only in what was actually said - do not infer scope that
   wasn't requested.
2. **Search for duplicates and overlapping work** before anything else: query existing `AIW`
   issues (`mcp__atlassian__searchJiraIssuesUsingJql` with a JQL text search on the normalized
   summary's key terms, e.g. `project = AIW AND text ~ "secret scanning"`) across all statuses,
   not just open ones - a *Done* ticket that already covers the request is still a duplicate, not
   a green light to create a second one.
   - Exact or near-exact match found → do not create a new issue. Go to step 6 (reuse/update
     path).
   - Related-but-distinct work found (e.g. it partially overlaps, or a currently-blocked ticket
     already covers the same intent under different wording, like a DAST/staging-scan request
     matching an existing "Add OWASP ZAP baseline DAST scan on STAGING" ticket) → surface it and
     prefer reusing/extending it over creating a near-duplicate; only proceed to a new issue if
     the requirement is genuinely a distinct piece of work.
   - Nothing matches → proceed to step 3.
3. **Determine the parent Epic.** List the project's Epics (`mcp__atlassian__searchJiraIssuesUsingJql`
   with `project = AIW AND issuetype = Epic`) and match the normalized requirement against their
   summaries/descriptions. An Epic fits if the requirement is clearly a piece of that Epic's
   already-described scope - not merely "vaguely related." If `knownContext.suspectedEpic` was
   given, verify it the same way rather than trusting it outright.
   - A fitting Epic exists → use it.
   - No existing Epic fits → this is the only case where creating a new Epic is even considered,
     and only with explicit justification naming the genuinely new responsibility/domain (see
     Behavioral rules) - and even then, this skill drafts the case, it does not decide alone (see
     Stop conditions).
4. **Determine milestone/fix version**, when the target is actually known (e.g. explicitly stated,
   or unambiguous from the Epic's own current milestone). Leave it unset rather than guess when
   it isn't.
5. **Select issue type, priority, and labels** using this project's existing, observable
   conventions - not invented rules: `Task` for a small, distinct piece of work, `Story` for a
   feature/capability expressed as a goal (mirror how existing sibling tickets under the same
   Epic are typed); priority follows the Epic's own general priority pattern unless the
   requirement states urgency explicitly; labels reuse existing labels already in use on sibling
   tickets under the same Epic (`mcp__atlassian__searchJiraIssuesUsingJql` on that Epic's children)
   rather than inventing new ones for the same concept.
6. **Reuse/update path** (only reached from step 2's duplicate/overlap branch): if the existing
   ticket fully covers the requirement, report it as-is - no edit needed. If it needs a scope
   adjustment to cover the new request, propose the specific edit (never silently apply it -
   `mcp__atlassian__editJiraIssue` is `external-visible`, same approval rule as ticket creation
   below).
7. **Assemble the ticket** using the required structure below, then create it
   (`mcp__atlassian__createJiraIssue`) - only after steps 2-5 are resolved, and only with the
   approval this skill's `external-visible` category always requires (see Allowed action
   categories).

## Required ticket structure

Every ticket this skill authors includes, in the description:

- Summary (also the issue's title).
- Context / Problem.
- Goal.
- Scope.
- Out of Scope, when useful to prevent scope creep.
- Implementation Notes, **only** when grounded in already-known repository architecture - never
  speculative.
- Acceptance Criteria, written as verifiable, observable outcomes (e.g. "CI blocks merge when
  the scan finds a high-confidence secret," never "secrets are handled well").
- Dependencies / related tickets, where known (issue links, not just prose mentions).
- Security, migration, or rollout considerations, when relevant.

Plus, as Jira fields (not description prose): parent Epic, milestone/fix version (when known),
priority, labels.

## Output / result contract

A single `ticketAuthoringResult` object:

```json
{
  "requirement": "...",
  "duplicateCheck": {
    "performed": true,
    "matches": [{ "key": "AIW-95", "summary": "...", "status": "Done", "relationship": "exact-duplicate | overlapping | related-not-duplicate" }]
  },
  "action": "created | reused-existing | proposed-edit | blocked",
  "issue": {
    "key": "AIW-131",
    "summary": "...",
    "issueType": "Task",
    "parentEpic": { "key": "AIW-8", "summary": "CI / CD Developer Experience" },
    "fixVersion": "M2 - Designer Agent",
    "priority": "Medium",
    "labels": ["..."]
  },
  "epicDecision": {
    "matchedExisting": true,
    "newEpicJustification": null
  },
  "unresolvedFields": ["fixVersion - not stated or inferable, left unset"],
  "awaitingApproval": true
}
```

`issue` is `null` when `action` is `"reused-existing"` (no new issue created - `duplicateCheck.matches[0]`
is the answer) or `"blocked"`. `epicDecision.newEpicJustification` is `null` unless a genuinely new
Epic was proposed, in which case it names the specific new domain/responsibility, never a vague
"doesn't quite fit anywhere." `unresolvedFields` lists every ticket field left unset because it
wasn't knowable - never silently omitted without a trace. `awaitingApproval` is `true` whenever
`action` would perform (or already staged) an `external-visible` write - this skill drafts the
ticket and stops for the same approval this project requires for every other externally visible
action (see Authority); it is not itself the approval.

## Stop conditions

- The requirement is too vague to normalize into a concrete summary and goal - report what's
  missing, do not guess a scope to fill the gap.
- Duplicate search is inconclusive (ambiguous overlap that can't be confidently classified as
  duplicate, related, or unrelated) - surface the candidates and their relationship as understood,
  do not pick one interpretation silently.
- No existing Epic fits and creating a new one is the only remaining option - draft the
  justification and stop; a human decides whether a new Epic is actually warranted, this skill
  never creates one unilaterally.
- A Jira tool call fails or access is denied - report the failure, do not substitute a fabricated
  result to keep going.

## Allowed action categories

- `external-read` - searching and reading Jira issues/Epics to check for duplicates and determine
  the right Epic; always performed before any write.
- `external-visible` - creating a new issue (`createJiraIssue`) or editing an existing one
  (`editJiraIssue`) to reuse/extend it. Per AIW-111, both require the same explicit human approval
  this project already requires for every other externally visible action ("ja push"/"ja merge"
  or equivalent, per action) - this skill assembles and stages the ticket content, it never
  creates or edits Jira issues unattended.

## Authority

A real, current, specific human approval is authoritative over this skill's own classification
judgment - if the human disagrees with the proposed Epic, duplicate call, or new-Epic
justification, their judgment wins and the ticket is redrafted accordingly, not created as
originally proposed. Per AIW-111's own enforcement (`.claude/settings.json`,
`mcp__atlassian__createJiraIssue`/`editJiraIssue` are `ask` rules), this is backed by the same
technical approval boundary as `git push`/PR creation, not prose discipline alone.
