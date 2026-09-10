# Secret management for AI provider credentials (AIW-64)

How real AI provider API keys (Anthropic, OpenAI) get to the backend, in every environment
this platform runs in.

## Local development

Unchanged from the existing pattern: copy `.env.example` to `.env`, fill in a real key, and
export it into the shell before starting the backend (`set -a && source .env && set +a`).
`.env` is git-ignored (see `.gitignore`) and never committed. `docker compose` also reads
`.env` automatically for the Postgres container's own credentials.

This is deliberately unchanged - AIW-64 is about production/staging, which has no equivalent
mechanism yet.

## Production / staging: Azure Key Vault

**Decision:** secrets are stored in Azure Key Vault and injected into the backend process as
environment variables at deploy time (a Key Vault reference on the Container App / App
Service configuration, resolved by the Azure platform itself before the container starts) -
not fetched by application code at runtime.

**Why this shape, not a Key Vault SDK call from the backend:**

- The backend already reads every credential from a plain environment variable
  (`ANTHROPIC_API_KEY`, `OPENAI_API_KEY` - see `application.yml`'s `real-ai` profile block and
  `AnthropicProperties`/`OpenAiProperties`). A platform-level Key Vault reference keeps that
  exact same code path in every environment; only *where the environment variable's value
  comes from* changes between local dev (`.env`) and Azure (Key Vault).
- No new dependency, no Key Vault SDK, no extra startup-time network call or failure mode to
  handle inside the application itself - one less thing that can make the backend fail to
  start for a reason unrelated to its own configuration.
- Secret rotation becomes an infrastructure concern (update the Key Vault secret, restart/
  redeploy the revision) rather than something the application needs to poll or react to. This
  platform doesn't currently need live, in-process secret rotation without a restart; revisit
  if that requirement ever appears.

**Why Key Vault specifically:** it's Azure's standard secret store, and this platform's
target infrastructure is Azure (see the AIW-67..70 epic: Azure environment architecture,
Terraform, Container Registry, Docker image). Provisioning the actual Key Vault, its access
policy/managed identity, and the Container App secret references is infrastructure work
tracked under that epic - this document records the decision the AIW-64 acceptance criteria
asks for; it does not itself stand up any Azure resource.

## No key ever appears in code, the frontend bundle, git history, or logs

- Every credential is read from an environment variable with an empty-string default
  (`AnthropicProperties`, `OpenAiProperties`) - never hardcoded, never a fallback to a real
  value.
- `.env` is git-ignored; only `.env.example` (with blank placeholder values) is committed.
- `AnthropicProperties`/`OpenAiProperties` override `toString()` to redact the key, so an
  accidental `log.debug("{}", properties)` or a future actuator `/configprops`-style endpoint
  can't print it verbatim - Java records otherwise generate a `toString()` that prints every
  component as-is.
- The frontend never touches an AI provider credential at all: it only ever calls this
  platform's own backend API, which is the sole thing that talks to Anthropic/OpenAI.

## Missing credentials fail startup clearly

`ModelProfileCredentialsValidator` checks, at startup, every configured
`architech.ai.model-profiles` entry against the provider it's wired to
(`AiProvider.isConfigured()`) and fails context refresh immediately - the same fail-fast
behavior a missing datasource property already gets (AIW-18/20) - if a profile is wired to a
provider missing its required credentials. This only fires for a profile actually wired to a
real provider; the platform still starts fine on `MockAiProvider` with no credentials at all,
and a provider that's registered but not wired to any profile (e.g. `openai` today, per
AIW-62) is never checked.
