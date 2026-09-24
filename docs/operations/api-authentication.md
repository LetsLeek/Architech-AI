# API authentication (AIW-185)

How `/api/**` is protected, what that protection actually buys, and - just as importantly -
what it does not.

## What this is

A single shared secret, sent as an `X-API-Key` header on every `/api/**` request and checked by
`core.security.ApiKeyAuthenticationFilter`. Before AIW-185 the backend had **no authentication of
any kind**: every endpoint was reachable by anyone on the open internet.

## What this is not

This is **not** per-customer authentication. There is no `User` entity, no login flow, no
sessions, and `Project` still has no owner column - two different people holding the same key see
exactly the same data. It matches this project's current single-operator posture.

Real multi-tenant authentication - accounts, a login UI, and ownership checks retrofitted onto
every existing endpoint (Projects, Requirements, Designer, Developer, QA) so one customer cannot
read another's projects - is a separate, much larger piece of work, roughly the size of one of
the M1-M4 agent epics. It is deliberately not attempted here.

## Honest limitation: the key is in the public bundle

The frontend is a statically-hosted SPA with no server side of its own, so its copy of the key is
baked into the JS bundle at build time (`VITE_API_KEY`, see `frontend/src/api/http.ts`). Anyone
who opens devtools on the real deployed site can read it.

So what this actually stops:

- ✅ Automated scanners, crawlers and opportunistic probing of the open internet
- ✅ Anyone who has the backend URL but has never loaded the frontend
- ❌ **Not** a determined person who already has the real page open in a browser

That trade-off is inherent to "static SPA + no user accounts," not an implementation shortcut -
closing it properly requires real per-user authentication (above), not a better-hidden shared
key. It is recorded here rather than left for someone to discover.

## Where the key comes from, per environment

| Environment | Value | Delivered via |
|---|---|---|
| Local dev / tests / CI | `architech-dev-api-key` (a real, committed, non-secret default) | `application.yml`'s own `${API_KEY:...}` fallback - zero setup needed |
| DEV / STAGING / PROD | A distinct 40-char `random_password` per environment | Terraform → that environment's own Key Vault → Container App secret env var `API_KEY` |

Each environment generates and stores its own independent value, in its own already-isolated Key
Vault - a DEV credential has no access path to a STAGING or PROD secret, matching the isolation
guarantee documented in `azure-environment-architecture.md`. The committed default is not a
secret and protects nothing: it exists purely so a fresh clone, the test suite, and CI all work
without configuration, exactly like `spring.datasource.password`'s own `architech` fallback.

## What is exempt

`/actuator/**` is deliberately unauthenticated. Every deploy workflow's own post-deployment
health check (`.github/actions/backend-smoke-test`) polls `/actuator/health` before anything
else, and `management.endpoint.health.show-details: never` (already set, predating this ticket)
means it exposes only `{"status":"UP"}` - no configuration, no internals.

## Rotating the key

1. `terraform apply -replace=random_password.backend_api_key` in that environment's own root
   (`infrastructure/environments/<env>`). This writes the new value into Key Vault and rolls the
   Container App onto a revision reading it.
2. For the frontend, update that environment's `VITE_API_KEY_*` GitHub Actions secret to the new
   value (`terraform output -raw backend_api_key`) and re-run its deploy workflow - the key is
   baked in at build time, so an un-rebuilt frontend keeps sending the old key and will start
   getting 401s.

The deploy workflows read the key straight from `terraform output` (or, for
`backend-rollback.yml`, from Key Vault directly - it never runs terraform), so their own smoke
tests need no manual update on rotation.

## Verifying it works

```bash
# 401 - no key
curl -i https://<backend>/api/projects/00000000-0000-0000-0000-000000000000

# 404 - real key, request reaches the controller, project genuinely doesn't exist
curl -i -H "X-API-Key: <the real key>" https://<backend>/api/projects/00000000-0000-0000-0000-000000000000

# 200 - health is never gated
curl -i https://<backend>/actuator/health
```

`ApiKeyAuthenticationFilterIT` asserts exactly these four behaviors against the real wired filter
chain on every CI run.
