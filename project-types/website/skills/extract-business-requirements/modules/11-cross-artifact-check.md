# 11 — Cross-Artifact Check

Keep the artifact boundary intact:

- Customer Profile = supported customer/business facts.
- Website Requirements = supported website intent.

A fact existing in the Customer Profile does not automatically create a requirement to publish it.

A requirement remains valid even when the fact needed to fulfill it is missing, ambiguous, or conflicting. Record the unresolved information when relevant; do not invent the fact and do not delete the requirement to avoid the problem.

Website Requirements must not silently correct or resolve Customer Profile information. Customer Profile facts must not override explicit publication/content constraints.

Known internally does not mean required or permitted to publish.

Provided claims are not automatically website content requirements.

Avoid copying mutable profile values into requirements unless the exact value is itself part of the website-specific instruction.

Do not create JSON pointers or internal artifact identifiers to link the artifacts. When Core exposes an artifact fragment as evidence, use only its platform-provided opaque source reference.

Duplicate an unknown or conflict into Website Requirements only when it materially affects website intent, using `affects` where appropriate.

This check is an agent consistency pass. Platform validators remain authoritative.
