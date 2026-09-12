# Module 03 — Component Architecture

## Goal

Create a maintainable source structure with meaningful responsibilities and pragmatic reuse.

## Principles

- Component boundaries follow meaningful UI/behavior responsibilities, not every design node.
- Prefer local state unless shared/global state is actually needed.
- Prefer cohesion over maximal DRY; avoid generic renderer frameworks for one-off marketing pages.
- Reuse components when there is genuine repeated structure/behavior.
- Keep page composition understandable to a human maintainer.
- Use semantic names such as `ContactForm`, `ServiceCard`, `MainNavigation`, not opaque design IDs.
- Avoid premature infrastructure, state libraries or abstraction layers.
- Keep source organization conventional for React + TypeScript + Vite and the supplied scaffold.

## Updateability

The resulting project should be easy for a human or future authorized update agent to inspect and change without reverse-engineering generated metaprogramming.
