# Approval boundary enforcement (AIW-111)

This document describes how the developer-agent's approval boundaries — the "ja push" /
"ja merge" convention this project has followed by discipline since M1, and that
`human-review-publish` (AIW-110) formalizes as a skill-level checkpoint — are backed by real
Claude Code runtime enforcement rather than prose alone, what that enforcement actually covers,
and its honest limitations.

## What Claude Code offers

Two independent, composable mechanisms, both scoped to this repository via `.claude/settings.json`
and `.claude/hooks/`, and both applying identically to interactive sessions, headless (`-p`) runs,
and any subagent spawned from this session:

- **Permission rules** (`.claude/settings.json`'s `permissions.ask`/`permissions.deny`): pattern
  rules against the literal command text (for `Bash(...)`) or exact/wildcarded tool name (for
  `mcp__server__tool`). `deny` always wins over `ask`, which always wins over `allow` — a narrower
  matching `allow` never overrides a broader matching `deny`/`ask`.
- **A `PreToolUse` hook** (`.claude/hooks/block-destructive-git.sh`, registered for the `Bash`
  matcher): runs before every Bash call, reads the actual `tool_input.command` text, and can
  return `deny` (blocks outright) or `request` (falls through to the permission rules above). A
  hook's `deny` takes precedence even over an `allow` permission rule. There is no `ask` decision
  a hook can return — only `allow`/`deny`/`request` — so a hook cannot itself pop the interactive
  approval prompt; only a matching `ask` permission rule does that.

## What is actually enforced here

| Action class | Mechanism | Effect |
|---|---|---|
| `git push` (plain, any form) | `settings.json` `ask` rule | Native approval prompt — the technical form of "ja push" |
| `gh pr create` / `gh pr merge` / `gh pr close` | `settings.json` `ask` rules | Native approval prompt — the technical form of "ja merge" (and PR creation/closing) |
| `mcp__atlassian__createJiraIssue` / `editJiraIssue` | `settings.json` `ask` rules | Native approval prompt, matching how this session has already sought explicit confirmation for ticket creation/editing in practice |
| `mcp__atlassian__transitionJiraIssue` | *(deliberately unrestricted)* | Left unrestricted: this project's own established workflow already treats "ja mach weiter mit AIW-XXX" as explicit authorization to transition that same ticket's status — restricting it would contradict the session's own already-approved process, not strengthen it |
| Force-push (`-f`/`--force`/`--force-with-lease`), `reset --hard`, `clean -f*`, `branch -D`, `checkout -f`/`--force` | `settings.json` `deny` rules **and** the `block-destructive-git.sh` hook | Hard-blocked outright, in their plain invocation form (permission rule) and regardless of invocation style — absolute path, shell wrapper (`sh -c`/`bash -c`), subshell, option reordering (hook, broad regex on the literal command text) |
| Obfuscated/wrapped plain `git push` (absolute path, `sh -c`, `bash -c`, compound command) | `block-destructive-git.sh` hook | Denied specifically *because* it's obfuscated, forcing the plain `git push ...` form that the `ask` rule above actually catches — a hook cannot force `ask` itself, so this is how the two layers connect |

## Honest limitations (per this ticket's own acceptance criteria)

This is **defense-in-depth against accidental or non-adversarial bypass, not a hard, OS-level
security boundary**. Claude Code's own documentation is explicit about this, and it applies here
unchanged:

- A Bash permission rule matches the **command text Claude Code produces**, not the program it
  ultimately invokes. Invocation styles the hook does not specifically pattern-match (e.g. a
  wrapper program the hook's regex doesn't recognize, a language runtime's own subprocess call,
  a script written to a file and then executed) could still evade both layers.
  - Claude Code auto-strips some known-safe wrappers before matching (`timeout`, `time`, `nice`,
    `nohup`, `stdbuf`, `command`, `builtin`, `noglob`, safe env-var prefixes, bare `xargs`) — these
    do not create a bypass. Others (`direnv exec`, `devbox run`, `mise exec`, `npx`, `docker exec`,
    `watch`, `setsid`, `ionice`, `flock`) are **not** auto-stripped and are not specifically
    covered by `block-destructive-git.sh` either; a command routed through one of these could
    reach `git` without either layer recognizing it.
- The hook's regexes are deliberately broad for the genuinely destructive category (hard `deny`,
  no exceptions) but necessarily incomplete — new git subcommands or flag spellings that achieve
  the same effect (e.g. some other future destructive flag) are not enumerated here and would need
  a hook update to cover.
- Neither layer sandboxes the process at the OS level. A sufficiently adversarial, deliberately
  evasive invocation is not this document's claim to have stopped — this project's own actual use
  case is an LLM agent operating in good faith under a session-scoped, repository-scoped config,
  not an adversary trying to escape its own guardrails. If that threat model changes, the
  documented, strongest available fallback is OS-level sandboxing (containers, restricted service
  accounts, filesystem/network isolation) around the agent's execution environment — outside the
  scope of what `.claude/settings.json` and hooks can provide.

## What was actually verified, and what could not be

- **Verified in this session**: `block-destructive-git.sh` was run standalone with crafted stdin
  JSON covering plain `git push`, `git push --force`/`-f`/`--force-with-lease`,
  `git reset --hard`, `git clean -fd`/`-df`, `git branch -D`, `git checkout -f`/`--force`, an
  absolute-path force-push, a `sh -c`-wrapped plain push, and unrelated commands (`git status`,
  `rm -rf node_modules`, `git checkout -b`) — every case produced the intended
  `deny`/`request` decision. An early version of the hard-deny regex had a real bug (it required
  an extra space between `git` and the subcommand, so it silently never matched plain,
  unpadded destructive commands like `git push --force ...` — only padded/wrapped forms tripped
  it); this was caught by the same standalone testing and fixed before commit.
- **Not verifiable within this same running session**: whether `.claude/settings.json`'s
  `ask`/`deny` rules and the registered hook actually take effect on **live** tool calls in this
  conversation. Settings and hook registration load at session start, not hot-reloaded mid-session
  — the same limitation already encountered and documented for AIW-114's skill hot-reload. A fresh
  Claude Code session started in this repository is required to confirm the native approval prompt
  actually fires for `git push`/`gh pr create`/`gh pr merge` and that the destructive-action `deny`
  rules actually block those calls end-to-end, rather than relying only on the standalone hook
  script test above.
