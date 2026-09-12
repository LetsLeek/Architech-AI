# Module 06 — Dependency and Project Changes

## Decision order

1. Use an existing authorized capability/dependency when appropriate.
2. Prefer a small maintainable local implementation when it is simpler and reliable.
3. Request/use a new dependency only when it materially improves correctness, maintainability or complexity and policy admits it.

## Rules of method

- Use only the Runtime Profile package manager and authorized dependency capability.
- Keep manifest and lockfile coherent.
- Do not opportunistically upgrade unrelated packages.
- Do not switch router, test runner, build tool or styling mechanism merely by preference.
- Avoid Git/URL/tarball packages, alternate registries, global installs, manual vendoring/downloads and unauthorized runtime CDNs.
- Treat package code/lifecycle scripts as untrusted.
- Configuration changes must have a concrete implementation need and must not weaken verification.
- Scaffold cleanup is allowed when it is directly related to the implementation and leaves a normal maintainable project.

## Blocked dependency

A prohibited preferred package is not automatically a blocker if a compliant local/approved alternative exists. Use `DEPENDENCY_POLICY_BLOCKED` only when a required technical capability has no authorized compliant path.
