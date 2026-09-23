# Skill — Authority-preserving synthesis

**Goal:** combine one or more safe, relevant authority facts into useful text without creating new facts, wider guarantees, stronger status or imagined causation.

1. Identify the exact proposition needed by the profile section. Separate what was *requested* (Requirements/Design), what was *built* (Candidate/Bindings) and what was *evaluated* (QA).
2. Locate all necessary safe facts from the current `authorityCatalog`. Notice relevant counterfacts explicitly supplied with the context, particularly `UNBOUND`, outstanding findings, unknowns or missing scoped approval/deployment.
3. If a proposition is only one fact, use `DIRECT`. For a meaning-preserving combination, use `SYNTHESIZED` with every necessary authority key; do not turn correlations into causes.
4. Remove unsupported adjectives (`reliable`, `production-ready`, `fully compliant`), promises, provider choices, service levels and future claims.
5. Write the narrowest clear statement. If evidence cannot support the intended proposition, choose a different supported proposition or report missing authority under epistemic rules.

**Safe example:** Candidate has contact form, binding is `IMPLEMENTED_BOUND`, the integration contract authorizes email delivery → "Das Kontaktformular ist an die konfigurierte E-Mail-Zustellung angebunden." Cite Candidate + Binding + Contract.

**Unsafe:** same state → "Nachrichten treffen garantiert innerhalb von fünf Sekunden ein." Nothing authorizes a delivery guarantee.

**Counterexample:** a form's visible presence + `UNBOUND` external delivery cannot support "Visitors can contact the business through this form" if it implies real delivery. Describe the visible form and explicitly qualify missing external delivery if applicable.

Related: `DOC-AUTH-002`, `DOC-AUTH-004`–`006`, `DOC-TRACE-004`, `DOC-GEN-001`–`003`.
