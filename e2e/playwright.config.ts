import { defineConfig, devices } from '@playwright/test'

// AIW-92: boots the real frontend dev server and the real backend (against whatever Postgres
// is already reachable at the usual SPRING_DATASOURCE_URL - `docker compose up -d` locally,
// a service container in CI) so these tests exercise the actual running app end-to-end, not a
// mock of it. `reuseExistingServer` locally means a dev already running `npm run dev` /
// `./mvnw spring-boot:run` doesn't get killed or duplicated.
export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  // One worker in CI: the suite shares one backend+DB, and each spec creates its own Project
  // rather than relying on isolated fixtures, so parallel workers could observe each other's
  // data. Revisit (per-worker project namespacing, or a fixture that resets state) if the
  // suite grows large enough that sharding (AIW-92's own AC) becomes worth the complexity.
  workers: process.env.CI ? 1 : undefined,
  reporter: process.env.CI ? [['html', { open: 'never' }], ['github']] : 'list',
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  // AIW-102: applies to every toHaveScreenshot() call - `animations: 'disabled'` freezes CSS
  // transitions/animations before capturing (Playwright's own recommended setting to reduce
  // visual-test flakiness). Baselines are generated inside the official Playwright Docker image
  // (see e2e/README.md's "Visual regression" section) - locally-generated ones would almost
  // certainly diff against CI on font rendering alone, which isn't a real regression.
  expect: {
    toHaveScreenshot: { animations: 'disabled' },
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: [
    {
      command: 'npm run dev',
      cwd: '../frontend',
      url: 'http://localhost:5173',
      reuseExistingServer: !process.env.CI,
      timeout: 60_000,
    },
    {
      command: './mvnw --batch-mode --no-transfer-progress spring-boot:run',
      cwd: '../backend',
      url: 'http://localhost:8080/actuator/health',
      reuseExistingServer: !process.env.CI,
      timeout: 180_000,
    },
  ],
})
