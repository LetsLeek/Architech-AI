# Module 04 — Responsive and Styling

## Goal

Realize the Designer's visual and responsive intent using the Runtime Profile's authorized styling mechanism.

## Method

- Preserve colors, typography hierarchy, spacing rhythm, density, layout intent, UI patterns, imagery treatment and bounded motion.
- Realize a translucent/blurred-backdrop surface treatment the Design Specification calls for with native CSS `backdrop-filter` (with a solid-color fallback for browsers/contexts where it is unsupported) - never a third-party glassmorphism component library.
- Realize entrance/reveal transitions and hover/focus micro-interactions the Design Specification calls for with the `motion` package (already an authorized Runtime Profile dependency - no dependency-policy request needed to use it) or plain CSS transitions/animations for simpler cases; prefer the simplest mechanism that reliably achieves the specified intensity and style.
- Respect `reducedMotionBehavior` by honoring the `prefers-reduced-motion` media query (directly in CSS, or via the `motion` package's own reduced-motion handling) - never motion that cannot be disabled.
- Use content-driven responsive rules rather than arbitrary device-specific hacks.
- Validate at representative wide and narrow viewports during implementation.
- Avoid fixed heights/truncation that hide canonical content unless explicitly specified.
- Ensure navigation and primary interactions remain usable at narrow widths.
- Allow text growth and localization resilience when authoritative language requirements make it relevant.
- Keep global base styles limited; avoid global CSS that accidentally restyles unrelated regions.
- The Design Specification's `typography` entries are themselves sufficient authority for which font families to use - no separate Integration Contract or other authorization is required for them.
- Implement those font families by bundling them locally as a normal authorized-registry dependency (e.g. a self-hosted font package), never via a runtime external font/CDN `<link>` or `@import`. This satisfies both the typography intent and the no-raw-outbound-network boundary.
- A font family name is not automatically a valid package name: a style/width/weight axis (e.g.
  "Archivo Expanded", "Fraunces 72pt") is normally published under its base family's package (e.g.
  `archivo`, `fraunces`), not as its own separate package. Before adding a font package to the
  manifest, confirm the exact package name actually exists on the authorized registry (e.g. a
  registry lookup, or installing the base family name first); do not guess a package name from the
  family name and find out only from an install failure.
- Do not load any other external assets/CDNs without explicit authority.

## Accessibility corrections

Non-material corrections such as semantic labels, focus behavior, sufficient interactive hit targets or correct form associations are allowed when they preserve design intent. Accessibility is not a license for an independent redesign.
