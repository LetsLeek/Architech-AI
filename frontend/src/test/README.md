# Frontend test conventions (AIW-88)

## Tooling

Vitest + React Testing Library, running in a jsdom environment (`vite.config.ts`'s `test` block).
`src/test/setup.ts` registers jest-dom's matchers globally and is loaded once via
`test.setupFiles` — never import it directly from a test file.

## Where tests live

Colocated next to the file they test, never in a separate `__tests__/` tree:

- `src/api/http.ts` → `src/api/http.test.ts`
- `src/pages/ProjectsPage.tsx` → `src/pages/ProjectsPage.test.tsx`

Use `*.test.ts` for pure logic (no DOM) and `*.test.tsx` for anything that renders a component.

## What to test

- Pure modules (`src/api/*`): unit test the exported functions directly — mock `fetch` with
  `vi.stubGlobal('fetch', ...)`, don't reach for RTL.
- Components (`src/pages/*`, `src/components/*`): render with `@testing-library/react` and drive
  them with `@testing-library/user-event`, asserting on what a user would see (role, text,
  disabled state) rather than component internals. Mock the API module the component imports
  (`vi.spyOn(api, 'fnName')`) instead of mocking `fetch` at the component-test level.
- A component that calls `useNavigate` needs `react-router-dom`'s `MemoryRouter` as a wrapper, and
  `useNavigate` itself mocked via `vi.mock('react-router-dom', ...)` when the test wants to assert
  *where* it navigated rather than just that navigation happened.

## Running

```bash
npm test          # vitest run - single pass, used in CI
npx vitest         # watch mode, for local development
```
