# 03 — Normalization

Normalization may change representation but must not add meaning.

Use the least assumptive representation supported by the evidence.

Safe examples include deterministic normalization of an explicitly supplied country or language identifier. Unsafe examples include inferring a country from a city, inferring a currency from location, or adding a telephone country prefix.

For prices:
- preserve the original representation in `raw`;
- do not invent currency;
- do not turn approximate pricing into an exact fixed price;
- preserve irregular pricing semantics when the structured forms would change meaning.

For contact data:
- trim safely;
- do not invent phone prefixes;
- do not invent URL schemes such as `https://`.

For opening hours:
- normalize only unambiguous recurring weekly hours;
- a missing day does not mean closed;
- preserve unusual, appointment-only, seasonal, special-date, holiday, or unsafe-to-normalize hours in `raw`;
- do not force ambiguous overnight intervals into the V1 weekly structure.

Combine evidence only when it unambiguously describes the same entity or fact.

When safe normalization is not possible, preserve raw evidence and/or record an appropriate unresolved item instead of guessing.
