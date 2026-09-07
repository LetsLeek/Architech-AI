# 02 — Customer Facts

Extract a customer/business fact only when supplied evidence supports it. Plausibility is not evidence.

Safe structural interpretation is allowed; adding a new assertion is not. For example, an explicitly supplied country name may be normalized to its country code, but a city alone must not be used to infer a country.

Extract supported business identity, contact information, locations, offerings, opening hours, social links, and other fields defined by the Customer Profile schema.

Do not enrich facts using industry conventions, geography, typical services, likely contact details, or other world knowledge.

Keep service areas distinct from physical locations.

Split explicit lists when their members are unambiguous. Do not invent typical offerings.

Do not turn website intent into Customer Profile facts.

Omit unsupported optional values rather than creating null placeholders.

Do not choose a preferred canonical value when materially conflicting evidence remains unresolved.
