#!/bin/bash
# AIW-111: PreToolUse hook (Bash matcher) - defense-in-depth against bypassing
# .claude/settings.json's Bash permission patterns via invocation style (absolute path,
# shell wrapper, option reordering, quoting). Permission rules match the literal command
# text Claude Code produces; this hook instead greedily regex-matches the same *intent*
# regardless of how it's invoked, closing the most obvious bypass gap - see
# docs/developer-agent/APPROVAL_ENFORCEMENT.md for the honest limitations of both layers
# together (neither is a hard OS-level sandbox).
#
# Contract (Claude Code PreToolUse hooks): reads one JSON object on stdin
# ({tool_name, tool_input.command, ...}), writes a JSON decision to stdout, exits 0.
# permissionDecision "deny" blocks the call outright; "request" falls through to the
# normal permission-rule flow (so a plain, non-obfuscated `git push` still correctly
# reaches the `ask` rule in settings.json and prompts as usual).

set -euo pipefail

input="$(cat)"
command_text="$(echo "$input" | jq -r '.tool_input.command // empty')"

deny() {
  local reason="$1"
  jq -n --arg reason "$reason" '{
    hookSpecificOutput: {
      hookEventName: "PreToolUse",
      permissionDecision: "deny",
      permissionDecisionReason: $reason
    }
  }'
  exit 0
}

# Hard-deny regardless of invocation style (absolute path, wrapper, subshell, option
# reordering) - these are AIW-111's own explicitly named "destructive Git cleanup/reset"
# and "force push or history rewrite" categories, never automatable, no approval path
# short of a human running the command themselves outside this session.
if echo "$command_text" | grep -qE '(^|[/ ])git[[:space:]]+.*(push[[:space:]]+.*(-f\b|--force\b)|reset[[:space:]]+--hard\b|clean[[:space:]]+.*-[a-zA-Z]*f|branch[[:space:]]+-D\b|checkout[[:space:]]+.*(-f\b|--force\b))'; then
  deny "Blocked by AIW-111's approval-boundary hook: this matches a destructive Git action (force-push, reset --hard, forced clean, forced checkout, or forced branch delete) regardless of how it was invoked. This project never automates these - see docs/developer-agent/APPROVAL_ENFORCEMENT.md."
fi

# Sensitive-but-legitimate actions (plain push, PR create/merge) are meant to go through
# settings.json's `ask` rules on the standard invocation, which pop Claude Code's own
# approval prompt - deny only the *obfuscated* forms here (absolute path, shell wrapper,
# subshell), forcing the standard form that the ask-rule actually catches, rather than
# trying to force an "ask" decision from inside a hook (not a supported decision value).
if echo "$command_text" | grep -qE '(^|[;&|]|\bsh[[:space:]]+-c\b|\bbash[[:space:]]+-c\b).*\bgit[[:space:]]+push\b' \
   && ! echo "$command_text" | grep -qE '^git[[:space:]]+push([[:space:]]|$)'; then
  deny "Blocked by AIW-111's approval-boundary hook: this looks like an obfuscated/wrapped 'git push' (absolute path, shell wrapper, or compound command) rather than a plain 'git push ...' invocation. Use the plain form so the normal approval prompt applies."
fi

# No match - fall through to the normal permission-rule flow (settings.json's ask/deny
# rules, then the default permission mode).
jq -n '{
  hookSpecificOutput: {
    hookEventName: "PreToolUse",
    permissionDecision: "request"
  }
}'
