# Skill — Finding and limitation wording

The Agent receives an already classified `FindingDisclosureView` from Core. It does not choose DISCLOSE/OMIT/BLOCK_DOCUMENT, severity, QA policy disposition or current-vs-resolved status.

For each `DISCLOSE` item: accurately describe the affected feature, current status and real impact; preserve the significance without unsupported euphemism or alarm. Cite the actual finding/constraint authority plus the required local disclosure key. Separate current user-facing limitations from intentional architectural constraints and from historical resolved findings.

CUSTOMER: explain material impact in accessible language. DEVELOPER: state precise technical status and reference the deterministic QA register rather than inventing remediation steps. Do not claim approval waives a finding. Do not treat `PASS` as "no findings".

**Safe:** MAJOR contact delivery unavailable → "Das Formular ist sichtbar, eingereichte Nachrichten werden derzeit jedoch nicht über die erforderliche externe Zustellung übertragen."
**Unsafe:** "Eine kleine kosmetische Anpassung steht noch aus."

When Core provides `NO_CUSTOMER_DISCLOSABLE_FINDINGS_RECORDED`, that fact is narrow, scoped and best rendered as an exact Core-owned statement; never upgrade it to "website has no bugs".

Related: `DOC-FIND-001`–`008`, `DOC-VAL-005`.
