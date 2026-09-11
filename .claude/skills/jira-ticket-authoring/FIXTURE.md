# Worked examples

All four scenarios below are grounded in this project's own real Jira state (`AIW` project) as
of AIW-61's implementation, except scenario 4, which is explicitly illustrative - this project
has never actually had a requirement that didn't fit one of its existing Epics.

## 1. Duplicate detection - exact match (real: AIW-95)

**Requirement:** "We need something to stop API keys and tokens from ending up in the Git
history, and catch it in CI if it happens anyway."

**Step 2 duplicate search** (`project = AIW AND text ~ "secret scanning"`) finds `AIW-95`, *Done*,
summary "Add secret scanning and push protection" - description covers exactly this (push
protection where available, Gitleaks fallback in CI, rotation over removal). This is a real,
exact match, not a coincidence: AIW-95 was implemented earlier in this project's own session
history.

**Result:** no new issue created.

```json
{
  "requirement": "Stop API keys/tokens from entering Git history; catch it in CI if it happens anyway.",
  "duplicateCheck": {
    "performed": true,
    "matches": [{ "key": "AIW-95", "summary": "Add secret scanning and push protection", "status": "Done", "relationship": "exact-duplicate" }]
  },
  "action": "reused-existing",
  "issue": null,
  "epicDecision": { "matchedExisting": true, "newEpicJustification": null },
  "unresolvedFields": [],
  "awaitingApproval": false
}
```

`awaitingApproval` is `false` here - reporting an already-existing match performs no write at
all, so there is nothing to approve.

## 2. Existing-ticket reuse - related, not exact (real: AIW-97)

**Requirement:** "We should run some kind of security scan against the deployed staging app
before things go to production, not just static analysis of the source."

**Step 2 duplicate search** finds `AIW-97`, *To Do*, summary "Add OWASP ZAP baseline DAST scan on
STAGING" - not literally the same words, but the same intent (dynamic scanning against a running
STAGING deployment). Real project state: AIW-97 is still open specifically because it's blocked on
a real STAGING environment existing (Pillar 1/Azure infra, not yet provisioned in this session) -
not because it was overlooked.

**Result:** the existing ticket already covers the requirement fully; no scope edit needed, just
report it rather than opening a near-duplicate.

```json
{
  "requirement": "Run a security scan against the deployed staging app, not just static analysis.",
  "duplicateCheck": {
    "performed": true,
    "matches": [{ "key": "AIW-97", "summary": "Add OWASP ZAP baseline DAST scan on STAGING", "status": "To Do", "relationship": "overlapping" }]
  },
  "action": "reused-existing",
  "issue": null,
  "epicDecision": { "matchedExisting": true, "newEpicJustification": null },
  "unresolvedFields": [],
  "awaitingApproval": false
}
```

## 3. New-ticket creation - no duplicate found (real, grounded)

**Requirement:** "If an AI provider starts failing repeatedly, we shouldn't just keep retrying
and fallback-ing at full speed - we should back off and rate-limit outbound calls so one bad
provider doesn't burn through the whole budget in a retry storm."

**Step 2 duplicate search** (`project = AIW AND text ~ "rate limit"`, and a broader search on
"retry"/"budget") finds two *related but distinct* real tickets, not a duplicate: `AIW-65`
("Budget Guardrails for AI Usage", *Done* - hard/soft cost *limits*, not call-rate throttling) and
`AIW-66` ("Provider Fallback & Retry", *Done* - which provider to fall back to, not how fast to
retry). Neither actually implements outbound rate limiting/backoff - this is genuinely new,
distinct work, dependent on both.

**Step 3** Epic search matches `AIW-10` ("AI Platform Core") - both AIW-65 and AIW-66, the closest
existing siblings, are its children, and this requirement is squarely the same domain (AI
Gateway call behavior).

**Step 5** labels reuse this Epic's existing sibling convention (`ai-core`, plus `reliability`
matching AIW-66's own labeling for retry/resilience-shaped work); issue type `Task`, matching
AIW-66's own type for a similarly-scoped, distinct piece of gateway behavior; priority `Medium`,
matching both siblings.

```json
{
  "requirement": "Rate-limit/backoff outbound AI provider calls so a failing provider can't burn the budget in a retry storm.",
  "duplicateCheck": {
    "performed": true,
    "matches": [
      { "key": "AIW-65", "summary": "Budget Guardrails for AI Usage", "status": "Done", "relationship": "related-not-duplicate" },
      { "key": "AIW-66", "summary": "Provider Fallback & Retry", "status": "Done", "relationship": "related-not-duplicate" }
    ]
  },
  "action": "created",
  "issue": {
    "key": "AIW-131",
    "summary": "Add outbound rate limiting/backoff for AI Gateway provider calls",
    "issueType": "Task",
    "parentEpic": { "key": "AIW-10", "summary": "AI Platform Core" },
    "fixVersion": null,
    "priority": "Medium",
    "labels": ["ai-core", "reliability"]
  },
  "epicDecision": { "matchedExisting": true, "newEpicJustification": null },
  "unresolvedFields": ["fixVersion - not stated or inferable from the requirement, left unset"],
  "awaitingApproval": true
}
```

`awaitingApproval: true` - this is a staged draft, not yet created. `createJiraIssue` only
actually runs after a human approves this exact proposal (per AIW-111's `ask` rule on
`mcp__atlassian__createJiraIssue`), the same "ja push"/"ja merge"-shaped gate this project applies
to every other externally visible action.

## 4. Genuinely-new-Epic recommendation (illustrative - no real precedent in this project)

This project's own real Jira history has never actually produced a requirement that didn't fit
one of its ten existing Epics (Platform Foundation, CI/CD Developer Experience, Project & Input
Management, AI Platform Core, Source Context & Provenance, Artifact & Version Management,
Requirements Agent V1, Requirements UI & Review, Internal Engineering Agent Workflow & Skills,
Designer Agent V1) - so, unlike the three scenarios above, this one is necessarily hypothetical.

**Requirement (hypothetical):** "We want an in-app support chat so customers using the platform
can ask a human for help directly, with ticket history visible to our support team."

**Step 3** Epic search: nothing fits. This isn't CI/CD, AI Gateway behavior, requirements/design
artifact handling, or developer-agent tooling - it's a customer-facing support channel, a
genuinely new product responsibility with no existing Epic covering it even loosely.

**Result:** this skill drafts the new-Epic justification and **stops** - it does not create the
Epic itself (see Stop conditions: "a human decides whether a new Epic is actually warranted, this
skill never creates one unilaterally").

```json
{
  "requirement": "In-app support chat for customers, with ticket history visible to the support team.",
  "duplicateCheck": { "performed": true, "matches": [] },
  "action": "blocked",
  "issue": null,
  "epicDecision": {
    "matchedExisting": false,
    "newEpicJustification": "Customer-facing support/helpdesk channel is a genuinely new responsibility - none of the ten existing Epics cover end-user support interactions; closest is Requirements UI & Review (AIW-14), but that's requirement-authoring UI, not a support channel."
  },
  "unresolvedFields": [],
  "awaitingApproval": false
}
```

`awaitingApproval` is `false` here too, but for a different reason than scenario 1/2: this is a
hard stop (`action: "blocked"`), not a no-op reuse - nothing is staged for approval because the
Epic question has to be resolved by a human before a ticket can even be drafted.
