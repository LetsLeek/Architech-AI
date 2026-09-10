# Developer-agent skill contract (AIW-114)

Defines the common shape every developer-agent skill (AIW-60, 61, 104–112, and whatever gets
added after) must follow, so the workflow grows into a set of predictable, composable, testable
units instead of unrelated prompt files. This document is the contract; it does not itself
implement any real skill (AIW-60 etc. do that, each against this contract).

## What "developer agent" means here

Not a new backend service, and not another `core.ai`/`AiGateway`-driven structured-output agent
like the Requirements Agent. The developer agent is **an actual coding-agent session (Claude
Code, today) operating on this repository's own SDLC** - reading a ticket, investigating the
repo, planning, branching, implementing, verifying, and stopping at defined approval boundaries
for a human - formalizing the workflow this project's sessions have already been following
manually since M1. Its "runtime" is Claude Code's own skill system, not a Java service.

## Canonical location

- **Skill content**: `.claude/skills/<skill-id>/` - Claude Code's own fixed convention, scanned
  at session start. This is a deliberate, different physical format from
  `project-types/<type>/skills/` (loaded by `core.skill.SkillLoader`, assembled into one prompt
  for one structured LLM call via `AgentRunner`/`AiGateway`). The developer agent isn't that kind
  of agent - it drives many tool calls across a session, not one bounded model call - so reusing
  that loader directly isn't the right fit. What *is* reused: the same `id`/`version`/
  `description` shape and the skill-plus-sibling-metadata-file pattern those skills already use
  (`SKILL.md` + `skill.yaml`), for consistency and because a generic loader extension (AIW-113
  or later) can parse both families the same way if it ever needs to.
- **This contract, and any future contract/versioning notes**: `docs/developer-agent/` (sibling
  to `docs/core/` and `docs/operations/` - a distinct concern from either: not a runtime
  contract for `core.*`, not an ops runbook).

Each skill directory:

```
.claude/skills/<skill-id>/
  SKILL.md       # Claude-Code-native: frontmatter (name, description) + instructions
  skill.yaml     # structured contract metadata: id, version, inputs, outputs,
                 # preconditions, stopConditions, allowedActionCategories
  FIXTURE.md     # optional but encouraged: one worked example input -> output
```

## Skill identifier and versioning

- `id`: kebab-case, verb-noun, matches the directory name exactly (`jira-ticket-intake`, not
  `AIW-60` or `IntakeSkill`) - a Jira ticket number is what *created* a skill, never what it's
  *called*, since tickets are ephemeral and skills are the durable artifact.
- `version`: a plain integer (matches `project-types/`'s convention - not semver), starting at
  `1`. Bump it only for a **breaking** change to the skill's declared `inputs`, `outputs`,
  `preconditions`, or `stopConditions` - a wording/prompt refinement that doesn't change the
  contract doesn't bump it. There is exactly one directory per skill id; git history is the
  version log (no parallel `-v2` directories anywhere else in this repo, and no reason to start
  here).

## Required `SKILL.md` sections

```markdown
---
name: <skill-id>
description: <one line - when Claude Code should reach for this skill>
---

# <Title>

## Purpose
## When to use
## Inputs
## Preconditions
## Steps
## Output / result contract
## Stop conditions
## Allowed action categories
## Authority
```

`Purpose`/`When to use`/`Steps` are free-form instructions (this is still a prompt, not just
metadata). `Inputs`/`Output`/`Preconditions`/`Stop conditions`/`Allowed action categories`
restate - in human-readable prose - what `skill.yaml` declares structurally; the two must never
disagree, and `skill.yaml` is authoritative if they ever drift (SKILL.md is what an agent reads
to *act*; skill.yaml is what a conformance check or future orchestrator reads to *verify*).
`Authority` names what overrides the skill's own judgment when they conflict - almost always
"the human reviewer, via the same 'ja push'/'ja merge' approval this project already requires
for every branch/PR" (see `CONTRIBUTING.md`) plus, once AIW-111 lands, whatever it technically
enforces.

## `skill.yaml` fields

| Field | Required | Shape |
|---|---|---|
| `schemaVersion` | yes | `1` |
| `id` | yes | matches directory name |
| `name` | yes | human-readable title |
| `version` | yes | integer, see above |
| `description` | yes | one line |
| `inputs` | yes | list of `{name, type, description, required}` |
| `outputs` | yes | list of `{name, type, description}` |
| `preconditions` | no | list of plain-language conditions that must hold before starting |
| `stopConditions` | yes | list of plain-language conditions that mean "stop and hand back to a human," not "retry" |
| `allowedActionCategories` | yes | subset of the fixed vocabulary below |

## Allowed action categories

A **fixed, shared vocabulary** every skill declares against - not invented per-skill, so a
future policy/enforcement layer (AIW-111) has one list to reason about instead of free text.
Deliberately the same three-tier shape this project's own sessions already operate under (see
this session's own system prompt: reversible/local actions proceed freely, hard-to-reverse or
externally-visible actions need explicit human approval, a fixed set is never automated at all)
- not a new policy, a restatement of the one already proven across every AIW ticket this
project has shipped:

- `read-only` - reading files, running tests/builds, searching, `git status`/`log`/`diff`.
- `local-write` - editing/creating files in the working tree.
- `local-git` - `git add`/`commit`/`checkout -b` on a local branch. Never `push`, `merge`, or
  any remote-affecting git command - those are `external-visible` below regardless of how
  routine they've become.
- `external-read` - reading from Jira/GitHub/the web (fetching a ticket, reading a PR).
- `external-visible` - anything that sends, publishes, or changes shared/remote state: `git
  push`, PR create/merge, Jira transitions/comments, any message. **Always requires the same
  explicit human approval this project already requires** ("ja push"/"ja merge" or equivalent,
  per ticket/action) - a skill may prepare and stage such an action, it must never perform it
  unattended.
- `destructive` - anything hard to reverse (force-push, `reset --hard`, deleting a branch/file
  wholesale, dropping data). Not something a skill automates at all today; flagged here so a
  skill that *thinks* it needs this stops and asks, per AIW-111's own scope once it lands.

A skill declaring `external-visible` or `destructive` in `allowedActionCategories` is stating
"this skill's normal operation reaches this category," not "this skill may act on it
unattended" - the approval requirement is not something a skill can opt out of by omission, it's
inherent to the category itself.

## Composition (for AIW-113's orchestrator)

- A skill never invokes another skill directly. Composition is the orchestrator's job -
  chaining skill invocations, matching one skill's declared `outputs` to the next one's
  declared `inputs` by name. This keeps every skill independently testable and
  invocable in isolation (a developer can run `jira-ticket-intake` alone to sanity-check it,
  without the full chain existing yet).
- A skill's `SKILL.md` must be self-contained: readable and followable on its own, without
  requiring the reader to already have another skill's `SKILL.md` open. Shared context that
  would otherwise be duplicated across skills belongs in a **rule** (a cross-skill invariant,
  analogous to `project-types/website/rules/` but for developer-agent conduct - not yet needed
  for the single fixture skill this ticket ships, but the contract leaves room for
  `docs/developer-agent/rules/<rule-id>/RULE.md` once a second real skill actually needs one).
- `stopConditions` are composition boundaries too: if a skill hits one, the orchestrator does
  not proceed to the next skill in the chain - it stops the whole run and hands back to a
  human, the same way this session stops for genuine ambiguity rather than guessing.

## Design principles (from this ticket's own scope, restated)

- **Skill** = task-specific workflow/instructions (a `SKILL.md`).
- **Rule** = cross-skill invariant behavior, not tied to one task (a future `RULE.md`, same
  pattern as `project-types/website/rules/`).
- **Tool** = a technical capability (Bash, Edit, Git, the Jira/GitHub MCP tools this session
  already uses) - skills *use* tools, they don't redefine what a tool can do.
- **Permission/Hook** = the actual enforced boundary (Claude Code's permission-prompt
  mechanism today; whatever AIW-111 adds later). A skill's `allowedActionCategories` is a
  *declaration*, not enforcement - **skills must not duplicate security controls that should be
  technically enforced**, per this ticket's own principle. AIW-111 is where "external-visible
  requires approval" stops being a documented convention this contract asks skills to follow
  and becomes something that's actually blocked without it.
- Skills stay small, composable, and independently versionable - one skill, one job (mirrors
  `extract-business-requirements` being one skill with 13 *modules*, not 13 skills, because
  it's genuinely one AI task with compositional instruction phases; a developer-agent skill
  should be similarly scoped to one real step of the ticket-execution workflow, not the whole
  workflow at once - that whole-workflow view is AIW-113's orchestration layer, not a skill).

## Conformance

`docs/developer-agent/scripts/check_skill_conformance.py <skill-directory>` validates a skill
directory against this contract: `SKILL.md` frontmatter has `name`/`description` and `name`
matches the directory; `skill.yaml` has every required field in the table above; every
`allowedActionCategories` entry is in the fixed vocabulary. Exit code 0 on conformance, non-zero
with a specific reason otherwise - verified both ways against `.claude/skills/example-echo-skill/`
(this ticket's conformance fixture) and a deliberately broken copy before being considered done.

The fixture is a placeholder skill, not a real one - AIW-60 and later tickets implement the
actual developer-agent skills against this same contract; the fixture exists only to prove the
contract is checkable, not aspirational.
