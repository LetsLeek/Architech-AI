# Website Development Base (AIW-138)

Design-neutral platform scaffold used as the immutable initial state ("Development Base",
`B0`) every Website Developer Agent V1 execution starts from. Matches the frozen
`runtime-profile.v1` (`../profiles/runtime-profile.v1.yaml`): React + TypeScript + Vite,
client/static-build only, npm.

This scaffold intentionally contains **no customer information architecture, copy, brand,
layout, or design decisions** - `App.tsx`/`HomePage.tsx` are placeholders proving the
build/test/route pipeline works, not real content. A Developer execution replaces them
according to its one authorized target Design Proposal.

## Task contracts (`runtime-profile.v1.requiredTaskContracts`)

| Contract          | Command       |
|--------------------|---------------|
| `install-frozen`   | `npm ci`      |
| `typecheck`        | `npm run typecheck` |
| `lint`             | `npm run lint`      |
| `test`             | `npm run test`      |
| `build-production` | `npm run build`     |
| `run-local`        | `npm run dev` (or `npm run preview` after `build`) |

## Provisioning

Core/Workflow provisions this scaffold as a project's repository baseline (AIW-153) before
any Developer execution starts; the Developer itself never re-runs `npm create`/scaffolding
tools - it only edits an already-provisioned working tree.
