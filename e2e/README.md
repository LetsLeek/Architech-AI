# End-to-end tests (AIW-92)

Playwright tests that exercise the real frontend and backend together, against whatever
Postgres is reachable at the usual `SPRING_DATASOURCE_URL` (`docker compose up -d` locally, a
service container in CI) - not mocks of either app.

## Running locally

```bash
docker compose up -d   # from the repo root, if not already running
cd e2e
npm install
npx playwright install chromium   # first time only
npm test
```

`playwright.config.ts`'s `webServer` entries start the frontend (`npm run dev`) and backend
(`./mvnw spring-boot:run`) automatically if they aren't already running, and reuse them if they
are - no need to start either by hand first.

## Conventions

- One spec per critical user flow, named for the flow (`critical-flow.spec.ts`), not for a
  page or component - these are journeys, not unit boundaries.
- Selectors are role/text/placeholder-based (`getByRole`, `getByPlaceholder`, `getByText`) -
  never CSS classes or DOM structure, so refactoring a component's markup doesn't break a test
  that never cared about it.
- Where a label is ambiguous (e.g. "Add evidence" appears in both the free-text and structured
  input sections), scope the locator to the containing `section.input-section` rather than
  reaching for something more brittle.
- No arbitrary `page.waitForTimeout(...)` - wait on a real signal (a URL change, an element
  becoming visible/enabled) that actually indicates the state you're asserting on.

## Why the "happy path" ends in a validation failure

The platform's only registered `AiProvider` outside an opt-in real-AI profile is the
deterministic mock (`MockAiProvider` - always returns empty content), so a Requirements
Analysis run through the real running app can never itself produce a validation-passing
candidate; success is only reachable by calling the runner directly with a hand-built result
(see `RequirementsAnalysisRunnerIT`'s own docstring on the backend for the same fact, proven at
the integration level). Pointing this suite at a real provider would cost real money on every
CI run for a result the test doesn't need to prove anything more than the mock path already
does. So `critical-flow.spec.ts` asserts on the actual current behavior of the deployed
app - evidence submission and a run reaching a real, deterministic terminal state - rather than
a fabricated success. It still exercises every UI transition (idle → running → completed) a
successful run would.

## Accessibility checks (AIW-98)

`accessibility.spec.ts` runs `@axe-core/playwright` against every critical screen after it's
fully rendered (not mid-load). **Automated checks here are a floor, not a replacement** for
manual keyboard navigation and screen-reader review - axe-core can only catch mechanically
detectable issues (missing labels, invalid ARIA, contrast, ...), not things like "does this
flow actually make sense operated by keyboard alone."

Only `serious`/`critical` impact violations fail the test, matching AIW-87's severity-tiering
policy applied to axe's own impact scale - `moderate`/`minor` findings are still collected and
attached to the test result (`testInfo.attach('axe-violations', ...)`) for triage, never
silently dropped, just non-blocking. A documented exception (disabling a specific rule) needs an
inline comment explaining why it isn't a real barrier and what the follow-up path is, the same
way `.gitleaksignore`/`nosemgrep` require a reason elsewhere in this repo - none exist today,
the baseline is 0 violations at any severity across every screen.

## Sharding

Not needed yet (one spec, one worker in CI - see `playwright.config.ts`'s comment on why
workers are pinned to 1 there). If the suite grows large enough for runtime to matter,
Playwright's built-in `--shard` splitting is the way to go; revisit the single-shared-backend
assumption in `playwright.config.ts` first, since parallel workers would need either
per-worker project namespacing or a state-reset fixture to stay independent.
