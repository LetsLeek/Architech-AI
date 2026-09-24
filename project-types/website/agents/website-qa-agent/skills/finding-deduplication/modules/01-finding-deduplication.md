# Skill — Finding Deduplication

Normalize repeated descriptions of the same logical defect within one Candidate/QA execution.

Compare structured attributes first: findingCode, Domain, normative basis, route, viewport, locale, interaction state, anchors and Evidence.
One defect with multiple Evidence items should normally become one Finding.
Do not over-merge separate user-impacting violations merely because they share technical root cause.
Prefer a specific Finding Code over the generic domain fallback for the same defect.
Preserve materially relevant Evidence/normative bases.
Do not merge across different Candidates or mutate historical Findings.
