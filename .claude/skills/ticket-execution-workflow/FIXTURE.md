# Worked examples

All three scenarios are real, not synthetic dry-runs - this project's own session history has
already executed the full ten-phase sequence, and hit both a real publish failure and a real
ambiguous-approval moment, before this orchestrator was ever written down. That's stronger
evidence than a fixture built for the occasion, and it's what AIW-113's own acceptance criterion
("a representative ticket can be executed end-to-end... in a non-production/test repository or
dry-run fixture") is grounded in here.

**Honesty note, upfront:** for scenario 1, each phase below was performed *in substance* - the
same inputs gathered, the same kind of output produced, matching each composed skill's own
declared contract - not through a literal `Skill` tool invocation of each sub-skill by name (this
session did the equivalent work directly, since "the developer agent" described in
`CONTRACT.md` *is* a Claude Code session operating on this repository, not a separate service
that must dispatch through a call boundary). This is not a shortfall of the sequence itself, just
an honest statement about *how* it ran, matching the same honesty this project has already
applied to AIW-111's own runtime-limitations section.

## 1. Full ten-phase execution (real: AIW-112)

| # | Phase | Real evidence |
|---|---|---|
| 1 | Jira ticket intake | `AIW-112` fetched via `getJiraIssue` - summary "Create post-implementation Jira update workflow," full AC and behavioral rules extracted. |
| 2 | Repository investigation | Inspected the real `core/error/` package structure, existing skills' `skill.yaml` shapes, and (critically) fetched AIW-110's real Jira changelog directly to verify actual status-transition practice, rather than assuming it. |
| 3 | Implementation planning | Designed the skill's four-`trigger` structure (`post-local-completion`/`post-publish`/`post-merge`/`blocked`) and its deterministic status table, directly informed by phase 2's real findings. |
| 4 | Safe branch preparation | `git checkout -B develop origin/develop` (synced first) → `git checkout -b AIW-112-post-implementation-jira-update`, off a clean, up-to-date `develop`. |
| 5 | Code implementation | `SKILL.md` + `skill.yaml` + `FIXTURE.md` written for `post-implementation-jira-update`; no backend/frontend code touched, so no coding-convention skill (e.g. `backend-error-handling`) applied for *this particular* ticket - correctly not invoked, since it genuinely wasn't relevant. |
| 6 | Implementation verification | `check_skill_conformance.py .claude/skills/post-implementation-jira-update` → OK; `semgrep --config auto` → 0 findings; `gitleaks protect --staged` → no leaks found. |
| 7 | Acceptance-criteria verification | All of AIW-112's own listed AC bullets checked against the real files: versioned skill exists, PR/branch links included in the output contract, status transitions deterministic and grounded in real observed states, partial/failed work explicitly cannot reach `Done` (the `blocked` trigger), fixtures cover all four required paths. |
| 8 | Human review checkpoint | Summary presented in chat: changed files, checks run, real grounding used. Explicitly stated nothing had been pushed yet. |
| 9 | Approved publication | Human replied "ja push" (a later, separate "ja merge" was required too, per this project's own two-step approval convention) → commit `bee0083`, push, PR `#84` opened with `--base develop` explicitly. |
| 10 | Post-implementation Jira update | After PR creation: reported PR link (`post-publish`-shaped). After the human's "ja merge" and the real merge (fast-forward `6529f0b..63fa293`): `transitionJiraIssue` → `Done` (`post-merge`-shaped) - matching this skill's own real, observed `In Progress` → `Done` practice, no `In Review` step. |

```json
{
  "issueKey": "AIW-112",
  "phases": {
    "jira-ticket-intake": { "status": "complete" },
    "repository-investigation": { "status": "complete" },
    "implementation-planning": { "status": "complete" },
    "safe-git-branch": { "status": "complete" },
    "code-implementation": { "status": "complete" },
    "implementation-verification": { "status": "complete" },
    "acceptance-criteria-verification": { "status": "complete" },
    "human-review-publish-review": { "status": "complete" },
    "human-review-publish-publish": { "status": "complete" },
    "post-implementation-jira-update": { "status": "complete" }
  },
  "haltedAtPhase": null,
  "haltReason": null
}
```

## 2. Publish failure and non-destructive recovery (real: AIW-111)

**What happened:** phase 9 (approved publication) for AIW-111 ran `gh pr create` without an
explicit `--base develop`. GitHub's configured default branch for this repository is `main`, so
the PR silently targeted and then merged into `main` instead of `develop` - `main` was ~115
commits behind `develop` at the time, so the squash-merge bundled *all* of that unrelated history
into one oversized commit on `main`, not just AIW-111's own 3 files.

**Recovery (Behavioral rules' "Publish failure"):** caught immediately by comparing
`origin/develop..origin/main` after the merge. Fixed with only reversible operations:

1. `git revert` of the wrong-target squash-merge commit on `main`, verified byte-identical to the
   pre-incident state via `git diff` before pushing - no force-push, no history rewrite.
2. A clean `git merge --no-ff` of the real AIW-111 commit onto `develop`, where it belonged.
3. The incident became a standing rule in this project's own memory - `gh pr create` must always
   name `--base develop` explicitly - rather than a one-off fix repeated by chance next time.

```json
{
  "issueKey": "AIW-111",
  "haltedAtPhase": "human-review-publish-publish",
  "haltReason": "PR merged into the wrong base branch (main instead of develop) - gh pr create omitted --base develop and silently used the repository's configured default branch.",
  "recovery": [
    "git revert of the wrong-target squash-merge on main (verified byte-identical to pre-incident state)",
    "git merge --no-ff of the real commit onto develop",
    "standing rule added: always pass --base develop explicitly"
  ],
  "resumedSuccessfully": true
}
```

## 3. Ambiguous approval - halt, ask, never guess (real: AIW-99)

**What happened:** after AIW-99's phase 8 review checkpoint was presented, the human's actual
reply was "ok dann weiter" - not a clear "ja push," not a clear rejection either.

**Result, per this skill's own orchestration-level stop condition:** the run stayed halted at
phase 8. A clarifying question was asked (via `AskUserQuestion`) rather than treating the
ambiguous reply as approval - only once the human's actual intent was confirmed did phase 9
proceed.

```json
{
  "issueKey": "AIW-99",
  "haltedAtPhase": "human-review-publish-review",
  "haltReason": "Human reply ('ok dann weiter') did not clearly name approval for this exact reviewed change set - genuinely ambiguous, not treated as \"ja push.\"",
  "action": "Asked a clarifying question via AskUserQuestion; stayed halted until the human's actual intent was confirmed."
}
```
