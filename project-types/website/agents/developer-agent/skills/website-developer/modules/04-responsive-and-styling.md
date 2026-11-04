# Module 04 — Responsive and Styling

## Goal

Realize the Designer's visual and responsive intent using the Runtime Profile's authorized styling mechanism.

## Method

- Preserve colors, typography hierarchy, spacing rhythm, density, layout intent, UI patterns, imagery treatment and bounded motion.
- Use content-driven responsive rules rather than arbitrary device-specific hacks.
- Validate at representative wide and narrow viewports during implementation.
- Avoid fixed heights/truncation that hide canonical content unless explicitly specified.
- Ensure navigation and primary interactions remain usable at narrow widths.
- Allow text growth and localization resilience when authoritative language requirements make it relevant.
- Keep global base styles limited; avoid global CSS that accidentally restyles unrelated regions.
- The Design Specification's `typography` entries are themselves sufficient authority for which font families to use - no separate Integration Contract or other authorization is required for them.
- Implement those font families by bundling them locally as a normal authorized-registry dependency (e.g. a self-hosted font package), never via a runtime external font/CDN `<link>` or `@import`. This satisfies both the typography intent and the no-raw-outbound-network boundary.
- Do not load any other external assets/CDNs without explicit authority.

## Accessibility corrections

Non-material corrections such as semantic labels, focus behavior, sufficient interactive hit targets or correct form associations are allowed when they preserve design intent. Accessibility is not a license for an independent redesign.
