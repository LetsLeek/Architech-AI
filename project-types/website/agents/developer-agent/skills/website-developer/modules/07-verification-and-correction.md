# Module 07 — Verification and Correction

## Progressive verification

Use the narrowest useful checks early, then broaden:

1. Relevant local/static checks
2. Typecheck / lint
3. Applicable tests
4. Production build
5. Local runtime
6. Routes / navigation
7. Representative responsive checks

These Developer-internal checks help you work; they do not replace final authoritative Runner Verification.

## Failure handling

Diagnose before changing code. Correct the actual cause rather than applying shotgun edits or suppressions.

- Developer-owned defect → fix within authorized correction allowance.
- Upstream/integration/runtime/tool capability outside your authority → semantic blocker only when it prevents meaningful completion.
- Runner/sandbox/infrastructure malfunction → do not modify source speculatively; execution layer handles `ERROR`.

## Anti-gaming

Do not delete/skip/neutralize tests, type safety, lint rules, build steps, required functionality or security checks merely to obtain green results. Configuration changes need legitimate project reasons.

## Final review

Before handoff, review `git diff`/workspace changes for unintended files, debug artifacts, secrets, stale TODOs, generated junk, unauthorized external runtime dependencies and accidental design/scope drift.
