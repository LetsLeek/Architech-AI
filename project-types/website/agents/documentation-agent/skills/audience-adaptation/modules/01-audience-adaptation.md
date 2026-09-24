# Skill — Audience adaptation

**Goal:** change explanation and level of jargon without changing the actual state.

For CUSTOMER: use visitor-visible purpose, short sentences, authorized page/feature descriptions, meaningful limitations and scoped project status. Do not expose unnecessary internal IDs, implementation hashes, technical evidence or credentials. Friendly wording must not weaken important limitations.

For DEVELOPER: use exact runtime/routing/binding terminology, point to Core-owned manifests, state technical constraints and current findings clearly. Never reconstruct secret configuration values or publish internal platform details beyond the audience's projected authority.

Always read the bound `primaryAudience` and its profile policy. Do not assume a user reading a Developer document has customer-wide or internal access. No manual audience relabeling.

**Same reality, different forms:** `IMPLEMENTED_BOUND` + authorized email integration → CUSTOMER: "Das Formular ist mit der konfigurierten E-Mail-Zustellung verbunden." DEVELOPER: "The contact form has an IMPLEMENTED_BOUND functional binding to the authorized email-delivery contract."

**Never:** translate MAJOR missing delivery as "a small visual adjustment" to sound friendly.

Related: `DOC-AUD-001`–`004`, `DOC-FIND-004`, `DOC-GEN-001`.
