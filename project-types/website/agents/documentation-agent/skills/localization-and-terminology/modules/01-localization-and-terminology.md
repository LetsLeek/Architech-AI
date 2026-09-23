# Skill — Localization and protected terminology

Generate directly from the same frozen Product Authority in the explicitly bound `targetLocale`; never translate a previous handover as the authority for another locale. The documentation language says nothing about the website's own language support.

- Maintain MUST/SHOULD/COULD requirement strength, known-vs-unknown, disputed claims, exact QA gate/severity/disposition, functional binding state and lifecycle scope.
- Canonical enums (`FULL_RELEASE`, `PASS`, `HOLD`, `UNBOUND`, `IMPLEMENTED_BOUND`) are locale-neutral structured values; audience prose may explain them but not redefine them.
- Preserve brand, registered legal name/suffix (e.g. `Beispiel GmbH`, not `Beispiel Ltd.`), routes (`/kontakt`), IDs, domains and technology names unless authorized localized authority explicitly replaces them.
- When **quoting** actual page labels, keep the authorized literal label; translating an explanatory sentence is different from claiming the deployed UI has translated labels.
- Formatting of structured dates/number/currency is Core/Renderer-owned, not model-generated arithmetic.

**Good:** `SHOULD` → "sollte" (German), subject to natural context; `UNKNOWN` remains unknown.
**Bad:** `SHOULD` → "muss"; `Beispiel GmbH` → "Example Ltd.".

Related: `DOC-LOC-001`–`006`.
