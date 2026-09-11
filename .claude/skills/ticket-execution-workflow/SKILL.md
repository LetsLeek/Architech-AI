---
name: ticket-execution-workflow
description: Orchestrates the full developer-agent ticket lifecycle - Jira intake through repository investigation, planning, branching, implementation, verification, acceptance-criteria checking, human review, approved publication, and post-implementation Jira update - by composing the other developer-agent skills in deterministic order. Use this as the entry point for working any AIW ticket end-to-end, not as a replacement for the individual skills it composes.
---

# Ticket Execution Workflow

## Purpose

Compose the ten developer-agent skills built under AIW-103 into one controlled, end-to-end ticket
execution workflow - without collapsing their separate responsibilities into a monolithic prompt.
This skill does not reimplement what `jira-ticket-intake`, `repository-investigation`, etc.
already do; it sequences them, carries their outputs forward as the next skill's inputs (matched
by name, per `CONTRACT.md`'s composition rule), and halts the whole run whenever a phase's own
stop condition fires - the same discipline this project's own sessions have followed manually
since M1, now written down as a real, checkable sequence instead of only convention.

## When to use

At the start of working any `AIW` ticket end-to-end - "ja mach weiter mit AIW-XXX" is this
skill's real-world trigger phrase in this project's own practice. Also re-entered mid-run to
resume after a human's review response (approve, reject/rework, or a materially changed code
state), without re-running phases that already completed for this same ticket and change set.

## Inputs

- `issueKey` (string, required on a fresh start): a Jira issue key, e.g. `AIW-113`.
- `resumeState` (object, required only when resuming a previously halted run): the accumulated
  phase outputs already produced (`implementationContext`, `repositoryContext`, `plan`,
  `branchResult`, `verificationReport`, `acceptanceCriteriaReport`, `reviewResult`) plus
  `haltedAtPhase` - carried forward so a resumed run never re-executes a completed phase (see
  Design rules).
- `humanResponse` (object, required only when resuming after phase 8's stop): the human's actual
  reply to the review checkpoint - approval naming the exact reviewed change set, a
  rejection/rework request, or (per this project's own established rule) neither, when the reply
  was genuinely ambiguous and needs a clarifying question before the run can proceed either way.

## Preconditions

- All ten composed skills exist and conform to `docs/developer-agent/CONTRACT.md` (verified via
  `check_skill_conformance.py`).
- Jira MCP tools, git, and this repository's own toolchain (Maven/npm/Playwright/etc., per
  whichever stacks a given ticket touches) are available for whichever phases the ticket actually
  reaches.

## Steps - the ten phases, in deterministic order

Each phase's real skill id, its exact inputs and outputs (matched by field name - the actual
mechanism `CONTRACT.md`'s composition rule describes), and what happens on that phase's failure.
"Halts the run" means: stop immediately, do not invoke any later phase, report which phase halted
and why - never silently skip ahead as if the phase had succeeded.

| # | Phase | Skill | Inputs | Output | On failure |
|---|---|---|---|---|---|
| 1 | Jira ticket intake | `jira-ticket-intake` | `issueKey` | `implementationContext` | Jira tool failure or unresolved issue key → retry once (a transient MCP hiccup has happened for real in this project); still failing → halt the run before any repository or Jira-mutating action, report plainly. |
| 2 | Repository investigation | `repository-investigation` | `implementationContext` | `repositoryContext` | Investigation tooling unavailable or the ticket's premise doesn't match the real repository state → halt before any code change; nothing has been written yet, so this is a clean stop. |
| 3 | Implementation planning | `implementation-planning` | `implementationContext`, `repositoryContext` | `plan` | A dependency this ticket needs isn't actually Done, or the ticket's scope can't be resolved into a concrete plan → halt, report the specific blocker (mirrors `jira-ticket-intake`'s own "is blocked by" stop condition surfacing here at planning time if it wasn't already caught at intake). |
| 4 | Safe branch preparation | `safe-git-branch` | `implementationContext` | `branchResult` | Working tree isn't clean, or a branch for this ticket already exists in an unexpected state → halt; never force past uncommitted work (this project's own git-safety rule, restated here, not a new one). |
| 5 | Code implementation | *(no single dedicated skill - the actual coding step)* | `plan`, `branchResult`, plus whichever coding-convention skill applies (today: `backend-error-handling` for backend failure cases; the architecture leaves room for others - frontend conventions, a future stack - without changing this phase's position in the sequence) | the real change set (files changed on the branch from step 4) | A check discovered *during* implementation that the plan itself is wrong (not just that a task is hard) → return to phase 3 with the new information, do not push ahead on a plan already known to be stale. |
| 6 | Implementation verification | `implementation-verification` | `plan` (informs which checks are relevant) | `verificationReport` | A required check fails and can't be fixed within the ticket's own scope → halt before review, report which check and why (per Design rule "later phases do not run when mandatory earlier phases fail"). A failure that *can* be fixed within scope (real precedent: AIW-102's visual-regression baseline mismatch) is fixed and re-verified here, not treated as a run-halting failure. |
| 7 | Acceptance-criteria verification | `acceptance-criteria-verification` | `implementationContext`, `verificationReport` | `acceptanceCriteriaReport` | `overallStatus` other than `complete` → halt before presenting a review checkpoint (this is that skill's own stop condition, enforced here at the orchestration level too, not just documented in isolation). |
| 8 | Human review checkpoint | `human-review-publish` (Phase 1) | `implementationContext`, `verificationReport`, `acceptanceCriteriaReport` | `reviewResult` (`phase: "review"`, `awaitingApproval: true`) | **Always halts here** - this is not a failure state, it is the workflow's own designed, expected stopping point every time, exactly as `human-review-publish` itself specifies. The run resumes only via `humanResponse` (see Inputs and Behavioral rules). |
| 9 | Approved publication | `human-review-publish` (Phase 2) | `reviewResult`, `approval` (naming the exact reviewed change set) | `reviewResult` (`phase: "published"`, `commitSha`/`branchName`/`prUrl`) | Approval doesn't name *this* exact change set, or the code changed materially since review → do not publish; return to phase 8 with the updated state for a fresh review, per that skill's own stale-approval rule. A push/PR-creation failure at the git/GitHub level (auth, branch protection, wrong target branch) is a real, recoverable failure - see Behavioral rules' publish-failure recovery, grounded in this project's own real incident. |
| 10 | Post-implementation Jira update | `post-implementation-jira-update` | `implementationContext`, `verificationReport`, `acceptanceCriteriaReport`, `reviewResult`, `trigger` (+ `mergeConfirmation` once actually merged) | `jiraUpdateResult` | A Jira write fails → report the failure; never claim a comment was posted or a status changed when it wasn't (that skill's own stop condition). Per that skill's own design, this phase is re-entered at multiple real points - after phase 8 (`post-local-completion`), after phase 9 (`post-publish`), and again once a human later confirms the PR was actually merged (`post-merge`, outside this skill's own numbered sequence, since merging itself is a separate human-gated action no skill in this chain performs). |

## Design rules

- **Each phase's inputs/outputs/failure states are exactly the table above** - not narrative
  intent, a literal, checkable data-flow contract matching each composed skill's own
  `skill.yaml`.
- **Later phases do not run when a mandatory earlier phase fails.** A halted run's state
  (whatever phase outputs already exist) is preserved in `resumeState`, never discarded - so
  fixing the blocker and resuming doesn't mean starting phase 1 over.
- **The workflow resumes after review/rework without losing context.** Phase 8's halt is the
  clearest case: the human's eventual response (approve/reject/ambiguous) is handled entirely via
  `humanResponse` on a later invocation of *this same skill*, reusing the already-produced
  `implementationContext`/`verificationReport`/`acceptanceCriteriaReport`/`reviewResult` - never
  re-running phases 1-7 just because time passed waiting for a reply.
- **Skills stay independently versionable and testable.** This skill's own `SKILL.md` names each
  composed skill by id and its exact declared inputs/outputs, never inlines their instructions -
  a skill can be improved, re-versioned, or tested in isolation (per `CONTRACT.md`) without this
  orchestrator's own text needing to change, as long as its `inputs`/`outputs` field names don't
  change.
- **External/destructive actions stay permission-controlled, never implicitly authorized by
  orchestration.** This skill sequencing phase 9 right after phase 8 does not itself grant
  approval - `human-review-publish`'s own halt and AIW-111's `.claude/settings.json` `ask`/`deny`
  rules apply exactly as they would if a human invoked each skill by hand. Orchestration composes
  *when* skills run, never *whether their own approval gates apply*.
- **Execution stays auditable.** Every phase's real output object (`implementationContext`
  through `jiraUpdateResult`) is the audit trail - what was read (`repositoryContext`), what was
  planned (`plan`), what was changed and tested (`verificationReport`), what was verified against
  the ticket (`acceptanceCriteriaReport`), what was reviewed and published (`reviewResult`), and
  what was recorded back to Jira (`jiraUpdateResult`) - a full run's `resumeState` at completion
  is that trail, not a separate log this skill has to maintain redundantly.

## Behavioral rules - failure/retry behavior (per this ticket's own acceptance criteria)

- **Jira access failure** (phase 1 or phase 10): retry once for a plausible transient failure
  (real precedent: a `getJiraIssue` call for AIW-106 hung and was retried fresh after a timeout,
  succeeding immediately) - never loop indefinitely; a second failure halts and reports, it does
  not retry again automatically.
- **Repository investigation failure** (phase 2): halts cleanly before any write - this phase is
  `read-only`, so there is nothing to roll back.
- **Test/verification failure** (phase 6): a fixable failure (within the ticket's own scope) is
  fixed and re-verified inline, not treated as a halt (real precedent: AIW-102's visual-regression
  baseline). An unfixable-in-scope failure halts before phase 7, per the table above.
- **Approval failure/ambiguity** (phase 8→9 boundary): an ambiguous human reply ("ok dann weiter"
  is this project's own real example) is never treated as approval - ask a clarifying question
  and stay halted at phase 8 rather than guess. A stale approval (the reviewed code changed
  materially since it was given - real precedent: AIW-99's own follow-up commit after its
  original "ja push") is never reused; return to phase 8 for a fresh review.
- **Publish failure** (phase 9): a git/GitHub-level failure is recoverable and must be fixed with
  non-destructive operations, never a forced overwrite. Real precedent, from this project's own
  history: AIW-111's PR was accidentally merged into `main` instead of `develop` because
  `gh pr create` wasn't given an explicit `--base develop` (it silently used the repository's
  configured default branch). Recovery used only reversible operations - `git revert` of the
  wrong-target squash-merge (verified byte-identical to the pre-incident state via `git diff`
  before pushing), then a clean `--no-ff` merge of the real commit onto `develop` - no
  force-push, no history rewrite, and the incident was turned into a standing rule (`gh pr
  create` must always name `--base develop` explicitly in this repository) rather than a one-off
  fix. Phase 9's own halt-and-report behavior applies the same way to any other publish-level
  failure (auth, branch protection, a required check newly failing after push).

## Output / result contract

A single `workflowExecutionState` object:

```json
{
  "issueKey": "AIW-104",
  "phases": {
    "jira-ticket-intake": { "status": "complete", "output": "implementationContext" },
    "repository-investigation": { "status": "complete", "output": "repositoryContext" },
    "implementation-planning": { "status": "complete", "output": "plan" },
    "safe-git-branch": { "status": "complete", "output": "branchResult" },
    "code-implementation": { "status": "complete" },
    "implementation-verification": { "status": "complete", "output": "verificationReport" },
    "acceptance-criteria-verification": { "status": "complete", "output": "acceptanceCriteriaReport" },
    "human-review-publish-review": { "status": "complete", "output": "reviewResult" },
    "human-review-publish-publish": { "status": "complete", "output": "reviewResult" },
    "post-implementation-jira-update": { "status": "complete", "output": "jiraUpdateResult" }
  },
  "haltedAtPhase": null,
  "haltReason": null,
  "resumeState": { "...": "every completed phase's real output object, for a later resume" }
}
```

While halted, `haltedAtPhase` names the phase (e.g. `"human-review-publish-review"`) and
`haltReason` states why in plain language - `null`/`null` only once phase 10's `post-merge`
trigger has actually run (see the phase-10 row above - the true end of a ticket's lifecycle, not
phase 9's publish alone).

## Stop conditions

Every composed skill's own `stopConditions` apply unchanged at the phase they belong to (see the
table above) - this skill does not loosen or duplicate them, it enforces that a stop in any phase
halts every later phase. Orchestration-level stop conditions, not owned by any single phase:

- A human's reply to the phase 8 checkpoint is genuinely ambiguous - stay halted, ask, never
  guess it means approval (or rejection).
- The reviewed code changes materially between phase 8 and a later `humanResponse` - the
  original review no longer applies; return to phase 8, do not publish against stale review.
- Any composed skill is missing, non-conformant (`check_skill_conformance.py` fails), or its
  declared `inputs`/`outputs` no longer match what this table assumes - halt before phase 1, this
  is an orchestrator-configuration problem, not a per-ticket one.

## Allowed action categories

The union of every phase's own declared categories - `read-only`, `local-write`, `local-git`,
`external-read`, `external-visible`. Never `destructive`: no phase in this sequence performs a
destructive action, and this skill does not add one by composing them. Composing phases 1-10
never expands what any individual phase is allowed to do - `human-review-publish` still needs a
real, current approval before phase 9's `external-visible` actions regardless of how this skill
sequences the run around it.

## Authority

Each composed skill's own `Authority` section governs within its phase - this skill does not
override any of them, it only sequences when each applies. Where a phase's own skill and this
orchestrator's restated table ever disagree (a changed `skill.yaml` this document hasn't caught
up to yet), the skill's own current `skill.yaml`/`SKILL.md` is authoritative, exactly as
`CONTRACT.md`'s own precedence rule states for every skill in this ecosystem.
