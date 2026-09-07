# 07 — Requirement Classification

Classify by semantic purpose, not keywords.

- WHY / desired outcome -> goal.
- WHO the website is intended for -> target audience.
- WHAT information must appear -> content requirement.
- Meaningful user/system behavior -> functional requirement.
- Website language availability -> language.
- Boundary, prohibition, navigation/placement rule, design restriction, business restriction, integration restriction, or technical restriction -> constraint.

Do not confuse a goal with a feature. A goal such as increasing inquiries does not itself create a contact-form requirement.

Do not confuse static content with functionality. Showing an address is content; requiring an interactive map is functional.

Ordinary navigation is not itself a functional requirement. An explicit navigation structure belongs in a navigation constraint.

An explicit default-language instruction may produce a language requirement and, when independently meaningful, a constraint.

Use `custom` only when the evidence clearly describes a requirement whose meaning is not represented by an available enum. Do not use `custom` as a container for ambiguity.

Create multiple entries only when each expresses independently supported intent. Prefer the narrowest representation that preserves the customer's meaning.
