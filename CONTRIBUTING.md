# Contributing

## Branching

- `develop` is the integration branch. `main` is not used for day-to-day work.
- One branch per Jira ticket, off `develop`: `<TICKET-KEY>-<short-slug>` (e.g. `AIW-25-project-persistence-model`).
- One PR per ticket, targeting `develop`.

## Merge requirements

`develop` has branch protection enabled: GitHub blocks merging a PR until these checks pass (enforced technically, not just by convention).

- **Backend tests** (from the "Backend CI" workflow): full Maven test suite against a real Postgres instance.
- **Frontend build** (from the "Frontend CI" workflow): `npm ci`, lint, `tsc` type-check + production build.

Both run on every PR into `develop`/`main`, regardless of which files changed. That's deliberate: a required status check that's skipped by a path filter never reports at all, which GitHub then shows as permanently "Expected" and blocks the merge button forever - a well-known GitHub Actions gotcha. Running both checks unconditionally (they take well under a minute combined) avoids that trap entirely.

Repo admins can currently bypass this (`enforce_admins` is off) for emergencies; that's a deliberate choice for a small team, not an oversight.

## Scope

CI here covers build/test/lint gating only. There is deliberately no production deployment pipeline yet — that's out of scope until closer to a real release.
