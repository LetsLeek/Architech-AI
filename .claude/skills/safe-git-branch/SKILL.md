---
name: safe-git-branch
description: Prepares a safe local Git branch for one ticket before implementation work begins - use this after implementation-planning and before editing any file, never skip it even when already "pretty sure" the worktree is clean.
---

# Safe Git Branch

## Purpose

Get to a state where implementation can safely begin: on the right branch, for the right
ticket, off the right base, with nothing at risk - before a single file is edited. This is the
one place in the chain so far that actually touches git (`local-git`), so it carries the most
direct responsibility for not destroying anything.

## When to use

After `implementation-planning`, before any file is created or edited for the ticket. Run it
even when you're confident the worktree is already clean - confidence isn't verification, and
`git status` costs nothing.

## Inputs

- `implementationContext` (object, required): `jira-ticket-intake`'s output - only `issueKey`
  and `summary` are actually used, to derive the branch name.

## Preconditions

- The current working directory is inside this git repository.
- The intended base branch (`develop` - see Branch naming below) is reachable locally.

## Steps

1. Run `git status` first - always, before any command that could discard or move
   uncommitted work. This is not optional even when the worktree is expected to be clean.
2. Determine the base branch: **`develop`, never `main`**, per this repository's own
   `CONTRIBUTING.md` ("`develop` is the integration branch... one branch per Jira ticket, off
   `develop`"). Confirm the current branch and whether it's already `develop` and up to date, or
   something else entirely (see Stop conditions for the latter).
3. Derive the branch name (see Branch naming) from `implementationContext.issueKey` and
   `summary`.
4. Check whether that branch name already exists locally or on the remote.
   - If it doesn't exist: create it off `develop` (`git checkout -b <name>`, from an
     up-to-date `develop`) - if the worktree has uncommitted changes belonging to *this* ticket
     (e.g. resumed after a context boundary), a plain `checkout -b` carries them onto the new
     branch safely, exactly as git already does by default; verify afterward that the base
     branch itself ends up clean.
   - If it already exists: check it out only if its state clearly indicates it's the same
     ticket's own prior work (e.g. its own commits reference the same issue key) - otherwise
     stop and report the collision rather than guessing whether reuse is safe.
5. Report the result - never proceed silently into implementation without confirming which
   branch is now checked out.

## Branch naming

**`<TICKET-KEY>-<short-slug>`** (e.g. `AIW-107-safe-git-branch-skill`) - this repository's own
actual, already-established convention (`CONTRIBUTING.md`, and every branch this project's
sessions have created), not the type-prefixed `feature/AIW-123-...` form this ticket's own
description offers only as an illustrative "such as" example. Per this contract's own design
principle (prefer existing patterns over inventing parallel abstractions - the same principle
`repository-investigation` and `implementation-planning` already apply to code), a documented
convention that already exists and is already followed everywhere in this repo wins over an
illustrative alternative from one ticket's description.

## Output / result contract

A single `branchResult` object:

```json
{
  "branchName": "AIW-107-safe-git-branch-skill",
  "baseBranch": "develop",
  "action": "created",
  "priorState": "clean-on-develop",
  "warnings": []
}
```

`action` is one of `created`, `reused`, or `stopped`. `priorState` is a short description of
what `git status` showed before acting (e.g. `clean-on-develop`,
`uncommitted-changes-on-develop`, `detached-head`). `warnings` is always present, empty when
there's nothing to flag.

## Stop conditions

- The repository is in an unexpected state (detached HEAD, mid-rebase, mid-merge) - report it,
  do not attempt to force a branch into existence from an unstable state.
- Uncommitted/untracked changes exist that a new branch/checkout could put at risk - report
  what's uncommitted and let a human decide (commit, stash, or confirm it's safe to carry
  forward) rather than guessing.
- The intended branch name already exists and its state doesn't clearly indicate it's safe to
  reuse for continuing the same ticket - report the collision.

## Allowed action categories

- `read-only` - `git status`/`log`/`branch` for inspection.
- `local-git` - `git checkout -b` on a local branch only. **Never** `push`, `merge`,
  `reset --hard`, `clean -fd`, a forced checkout, or a force-push - per this ticket's own
  explicit rules, none of that is "safe branch preparation," and none of it is in scope for
  this skill regardless of how routine it might seem mid-session. Branch creation does not
  imply permission to commit, push, or open a PR - those are separate steps gated the normal
  way (`external-visible`, explicit human approval).

## Authority

`git status`'s own output is authoritative over any assumption about worktree cleanliness - if
it disagrees with what was expected, the disagreement is what gets reported, not the
assumption. `CONTRIBUTING.md` is authoritative for branch naming and base-branch choice over any
ticket description's own illustrative example.
