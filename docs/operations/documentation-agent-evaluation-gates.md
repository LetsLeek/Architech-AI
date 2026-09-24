# Documentation Agent evaluation gates (AIW-204)

The Documentation Agent's own independent, model-backed release gates - separate from the offline
schema/deterministic checks `documentation-agent-ci.md` covers, and separate from each other.

## What these two gates check, and why they're kept independent

`FACTUAL_CONSISTENCY.md` (the frozen semantic validator spec) and `INTEGRATION_HANDOFF.md` point 9
both call for real-model evaluation before a release/config/model change, with generator and
validator fixtures kept genuinely independent: *"separate hand-authored semantic validator
fixtures from generator evaluations"*.

- **`DocumentationGeneratorEvaluationIT`** (`backend/src/test/java/ai/architech/backend/core/documentation/evaluation/`)
  asks: given a real, frozen `DocumentationContext`, does `documentation-reasoning` actually
  produce documentation that reflects it honestly? Four hand-authored scenarios: a known business
  name gets mentioned; unknown opening hours are never fabricated into a specific time; bound vs.
  unbound functional bindings are both described (not silently omitted); a disclosed finding is
  surfaced in the right section. Assertions are structural/content-presence only (schema validity,
  passing AIW-195/196's deterministic gate, case-insensitive keyword presence) - never exact text,
  since live model output is non-deterministic.

- **`DocumentationSemanticValidatorEvaluationIT`** (same package) asks the opposite question: given
  a hand-written claim with a *known, unambiguous* correct verdict, does `documentation-factual-consistency`
  actually classify it correctly? Three cases: an accurate claim citing a real fact →
  `SUPPORTED`; a claim that cherry-picks a real bound-binding citation while its own text ignores a
  genuine unbound sibling → `UNSUPPORTED`; a claim that softens a disclosed `CRITICAL` finding's
  real impact → `UNSUPPORTED` (the spec's own "separate disclosure check").

**Why independent fixtures matter**: if the same scenario backed both gates, a validator with a
blind spot could pass simply because the generator it's paired with happens not to trigger that
blind spot (and vice versa) - each gate's claims/facts are hand-written directly, never taken from
the other gate's own live output.

## These gates cost real money and never run automatically

[[architech_cost_conscious_testing]] governs every other test in this codebase precisely so these
two classes can be the deliberate exception. Both are tagged `@Tag("real-model-eval")` and excluded
from every default `./mvnw verify` - `backend/pom.xml`'s `test.excludedGroups` property (applied to
both `maven-surefire-plugin` and `maven-failsafe-plugin`) defaults to `real-model-eval`, so neither
a routine local build nor CI's own `Backend tests` job (which runs plain `./mvnw verify`, no
special flag) ever executes them or spends anything.

**To run them for real:**

```
./mvnw verify -Preal-model-eval
```

This activates the `real-model-eval` Maven profile, which clears `test.excludedGroups` back to
empty - the *only* way these two classes ever run. You also need `application.yml`'s `real-ai`
Spring profile active with a real Anthropic API key configured, or the tests will exercise the
mock provider and their assertions (which expect genuine model judgment) will fail.

**No CI workflow invokes this profile** - confirmed via `grep -rn "real-model-eval"
.github/workflows/` (zero matches) as part of landing this ticket, and it should stay that way.
Running this gate is a deliberate, infrequent, human-triggered action tied to an actual
release/config/model change, not a routine part of development or a PR check - each run makes
several real Anthropic API calls (one per generator scenario, one per semantic-validator case) at
real cost.
