# 09 — Strength Classification

Strength represents customer commitment only.

Use:
- `must` for direct requirements, required/prohibited behavior, and intended scope expressed as required;
- `should` for a clearly weaker preference;
- `could` only for an explicitly optional idea or nice-to-have supplied by the customer.

A polite direct request such as "Could you add a contact form?" is normally still `must` when its semantic intent is a request to include it.

Never use `could` for an idea invented by the model.

Do not change strength because of importance, implementation cost, difficulty, model confidence, best practice, platform policy, technical necessity, or legal significance.

If supplied evidence materially disagrees about commitment, preserve the disagreement rather than selecting the strongest or newest interpretation.

Strength does not determine readiness, retries, QA severity, or deployment.
