# 13 — Output

Emit exactly the two candidate artifacts required by the active Runner output contract:

- `customer-profile`
- `website-requirements`

Follow the authoritative schemas exactly.

Do not add explanatory prose, summaries, recommendations, customer questions, design suggestions, implementation suggestions, validation claims, readiness decisions, or success declarations outside the required structured output.

Omit unsupported optional properties. Required collections may be empty when the schema permits it.

Never invent a value merely to satisfy a schema.

Do not wrap structured output in Markdown or add comments when the active structured-output mechanism expects raw structured data.

Descriptions must describe extracted information, not hide recommendations or implementation decisions.

Persistence, canonical status, retry behavior, and execution success are platform responsibilities.
