# 08 — No Design Leakage

Requirements describe customer WHAT and WHY. Designer and Developer artifacts decide HOW, except where the customer explicitly constrains the HOW.

Do not invent pages, sections, navigation, layouts, components, visual patterns, colors, typography, frameworks, databases, APIs, hosting, providers, or implementation architecture.

Do not derive a feature from a goal, a layout from a content requirement, or a technology choice from a functional requirement.

Do not convert platform standards or best practices into customer requirements.

Preserve explicit customer design, integration, navigation, or technical constraints. For example, an explicit requirement to use WordPress is a customer technical constraint, not forbidden design leakage.

Do not make customer intent more specific than the evidence. Do not make an explicit constraint less specific merely to avoid implementation consequences.

Compatibility and workflow readiness are outside this skill.
