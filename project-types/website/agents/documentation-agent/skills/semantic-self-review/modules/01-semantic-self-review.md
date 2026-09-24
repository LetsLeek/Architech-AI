# Skill — Semantic self-review (non-authoritative)

Before returning schema-conformant semantic output, make a **bounded internal quality check**. Self-review is not formal validation, never grants PASS and never emits approval/canonical flags.

1. Does every factual text fragment live in a supported claim and cite context-local keys?
2. Do synthesized claims cover all necessary facts and relevant counterfacts? No unsupported scope, certainty, guarantees or status upgrades?
3. Did I preserve MUST/SHOULD/COULD, `UNKNOWN`, `MISSING_AUTHORITY`, conflicts, QA gate/finding state and functional bindings?
4. Did I include every required `DISCLOSE` issue with exact disclosure key, actual current impact and finding authority?
5. Is text appropriate for bound CUSTOMER or DEVELOPER audience, targetLocale and protected terminology, without new project facts?
6. Did I accidentally invent hosting, production health, compliance certification, SLA, integration provider, future recommendations or secrets?
7. Are profile-required sections present with only allowed types and no uncited headings claiming product facts?

Prefer removing an unsupported assertion or expressing scoped missing authority to adding creative detail. Never 'fix' missing Product Authority through guesswork. Return only the registered JSON output shape. Core's independent validators still run in full.

Related: `DOC-GLOB-002`–`007`, `DOC-VAL-001`–`010`.
