# Skill — Claim construction and traceability

**Goal:** produce a useful independently verifiable claim for every factual proposition.

- Form one coherent assertion per claim; a sentence may require more than one claim, and trivial grammatical fragments are not separate claims.
- Choose a registered `claimType`: CUSTOMER_FACT, REQUIREMENT_DESCRIPTION, DESIGN_DESCRIPTION, IMPLEMENTATION_DESCRIPTION, FUNCTIONAL_BEHAVIOR, INTEGRATION_DESCRIPTION, QA_STATUS, KNOWN_LIMITATION, APPROVAL_STATUS, DEPLOYMENT_STATUS, AUTHORITY_AVAILABILITY, UNKNOWN_REPRESENTATION, CONFLICT_REPRESENTATION or TECHNICAL_CONSTRAINT.
- Mark `DIRECT` for a faithful restatement of one fact; mark `SYNTHESIZED` only for justified cross-fact combinations.
- Select the **minimum sufficient** local `authorityKeys`; a claim about a real bound integration may need a Candidate fact + FunctionalBinding + IntegrationContract. Never generate global refs yourself.
- For required Known Limitations, attach the exact provided `disclosureKeys`. The disclosure key alone is not enough: the claim needs underlying finding/constraint authority and meaningful disclosure of its actual impact.
- Keep raw text plain and ready for structural rendering. Do not hide facts in headings, metadata or HTML/Markdown markup.
- If a context-state authority says `MISSING_AUTHORITY(APPROVAL)`, the maximum claim is "No ApprovalRecord is bound to this documentation context"; it cannot assert that customer never approved.

**Good:** `FUNCTIONAL_BEHAVIOR`, `SYNTHESIZED`, "The contact form uses the configured authorized email-delivery integration.", 3 necessary keys.

**Bad:** Claim says "all requested features are fully complete" backed only by Requirements, or attaches 20 unrelated authority keys to appear supported.

Related: `DOC-GLOB-006`, `DOC-CTX-006`–`007`, `DOC-TRACE-001`–`007`.
