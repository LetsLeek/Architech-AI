# Skill — Customer Fact Review

Evaluate Candidate claims against Customer Profile authority only.

Classify claims as:
EXACT_SUPPORTED_FACT | SUPPORTED_PARAPHRASE | UNSUPPORTED_EXPANSION | CONTRADICTION | UNKNOWN_MATERIALIZED | UPSTREAM_CONFLICT.

Use deterministic exact-fact checks where possible.
Supported paraphrase is allowed.
Unsupported factual expansion may create FACT_UNSUPPORTED_CLAIM.
Direct contradiction may create FACT_CONTRADICTION.
Core identity misrepresentation may create FACT_IDENTITY_MISREPRESENTATION.
Known Unknown concretization may create FACT_UNKNOWN_MATERIALIZED.
Do not independently research Customer truth.
Missing display of a fact is not automatically a Customer Fact defect; Requirement/Content authority must require presence.
