# Skill — Customer Handover composition

**Applicable only to:** `CUSTOMER_HANDOVER@1.0.0`. Profile controls the exact document/section taxonomy and order; this file is method guidance, not a replacement profile.

- `WEBSITE_OVERVIEW`: summarize authorized business/website purpose and the exact Candidate, without sales language.
- `WEBSITE_STRUCTURE`: explain only selected and actually implemented pages/routes; Core owns exact route manifest.
- `FEATURES`: explicitly distinguish requested functionality from implemented functionality; use FunctionalBinding when describing local/bound/unbound behavior.
- `CONTENT_AND_LANGUAGES`: distinguish content included from languages requested or supported; document targetLocale is not website language.
- `INTEGRATIONS`: explain only authorized bound integrations at a visitor/customer level, no secrets/provider invention.
- `KNOWN_LIMITATIONS`: include all Core-selected required disclosures faithfully. Where none are recorded, leave it to Core's narrow deterministic zero-disclosure proof, not an invented 'perfect website' claim.
- `PROJECT_STATUS`: describe only exact scoped QA, approval and deployment evidence. QA PASS is not approval or deployment; deployment is not Production Verification.
- `CHANGE_AND_MAINTENANCE`: describe only existing authorized platform change/support process; don't invent SLAs or free service commitments.

All required section keys must appear even where the profile explicitly permits an empty Agent-produced block array because Core inserts deterministic material. Do not add other sections. Technical/internal information may be excluded only according to policy; material known customer-impacting issues cannot be softened away.

Related: `DOC-PROF-004`, `DOC-AUD-001`–`003`, `DOC-FIND-004`–`007`.
