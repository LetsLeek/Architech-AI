# Worked examples

## 1. Clean worktree (representative of the normal case)

**Input:** `{ "issueKey": "AIW-107", "summary": "Create safe Git branch workflow skill for developer agent" }`

`git status` on `develop`, clean, up to date. This is the ordinary case for the large majority
of this project's own tickets - a fresh branch off an already-clean `develop`.

**Output:**

```json
{
  "branchName": "AIW-107-safe-git-branch-skill",
  "baseBranch": "develop",
  "action": "created",
  "priorState": "clean-on-develop",
  "warnings": []
}
```

## 2. Uncommitted changes safely carried forward (real: AIW-128)

**Input:** `{ "issueKey": "AIW-128", "summary": "fix combined-envelope and preamble JSON extraction" }`

**What actually happened** (this repository's own real history): work on AIW-128 began directly
on `develop` by mistake - uncommitted fixes existed there before a branch had been created.
Caught before pushing. The fix was `git checkout -b AIW-128-fix-envelope-and-preamble-extraction`,
which correctly carried the uncommitted working-tree changes onto the new branch (git's own
default `checkout -b` behavior), leaving `develop` itself clean afterward - confirmed via `git
status` on `develop` post-checkout, not assumed.

**Output:**

```json
{
  "branchName": "AIW-128-fix-envelope-and-preamble-extraction",
  "baseBranch": "develop",
  "action": "created",
  "priorState": "uncommitted-changes-on-develop",
  "warnings": [
    "Uncommitted changes were present on develop before this branch existed - they have been carried onto AIW-128-fix-envelope-and-preamble-extraction via checkout -b. Verified develop is clean after the checkout; if it weren't, that would itself be a stop condition, not something to proceed past."
  ]
}
```

## 3. Existing branch collision (illustrative)

**Input:** `{ "issueKey": "AIW-999", "summary": "some other change" }`, where a branch named
`AIW-999-some-other-change` already exists locally - but its most recent commit references a
completely different ticket (a stale, unrelated branch left over from earlier work, not a
resumed session for AIW-999 itself).

**Result:** stop, don't reuse it and don't delete it either.

```json
{
  "branchName": "AIW-999-some-other-change",
  "baseBranch": "develop",
  "action": "stopped",
  "priorState": "clean-on-develop",
  "warnings": [
    "A branch named AIW-999-some-other-change already exists locally, but its commit history references AIW-812, not AIW-999 - this looks like a stale, unrelated branch reusing a name, not a resumed session for this ticket. Not reusing or deleting it automatically; a human should confirm what it actually is before this skill proceeds."
  ]
}
```

## 4. Unexpected starting state - already on the base branch with real work in progress (real: AIW-91)

**Input:** `{ "issueKey": "AIW-91", "summary": "Add backend integration tests against real PostgreSQL behavior" }`

**What actually happened**: after merging a prior ticket, work on AIW-91 began - `git status`
was run and showed uncommitted, substantial changes already sitting on `develop` (over 30 file
renames plus new files) with no ticket branch created yet. This is exactly the state this
skill's own Step 1 (`git status` first, always) exists to catch before anything is committed -
in the real incident, it was caught by manually noticing "On branch develop" before committing,
which is precisely what this skill formalizes into a mandatory first step rather than something
that depends on noticing.

**Output:**

```json
{
  "branchName": "AIW-91-backend-integration-tests",
  "baseBranch": "develop",
  "action": "created",
  "priorState": "uncommitted-changes-on-develop",
  "warnings": [
    "Substantial uncommitted work (37 files) was already sitting on develop with no ticket branch - this is a process violation this skill exists specifically to catch. Carried the changes onto the new branch via checkout -b; confirmed develop is clean afterward."
  ]
}
```
