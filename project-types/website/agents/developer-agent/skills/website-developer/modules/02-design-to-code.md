# Module 02 — Design to Code

## Goal

Translate the target Proposal's visible semantics into maintainable source code without treating the design artifact as a literal runtime AST.

## Method

- Preserve canonical page/routes and navigation semantics.
- Preserve section order, purpose, content hierarchy and visible element intent.
- Implement semantic HTML and accessible interaction primitives where doing so does not materially change the design.
- Use actual source abstractions rather than rendering the entire Proposal JSON at runtime.
- Convert design specification values into technical tokens/variables where useful, while preserving visual meaning.
- Use canonical customer content as supplied/authorized. Do not perform material copywriting or invent SEO claims.
- Keep design `localRef` values out of normal DOM/component naming unless a true technical need exists.

## Element guidance

Implement heading/text/image/CTA/card-group/list/gallery/form/map/social-links/video/download/custom according to the Proposal and functional authority. A visible external-function surface does not itself authorize an external integration.

## Boundary

The framework must not drive a redesign. Do not independently modernize IA, reorder sections, add trendy UI patterns, collapse required content, or substitute unsupported assets.
