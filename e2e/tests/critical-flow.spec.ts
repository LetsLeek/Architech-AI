import { expect, test } from '@playwright/test'

// AIW-92: the platform's only registered AiProvider in this environment is the deterministic
// mock (see backend's MockAiProvider) - it always returns empty content, so a real
// Requirements Analysis run through this app can never itself produce a validation-passing
// candidate (see RequirementsAnalysisRunnerIT's own docstring for the same fact, proven at the
// integration level). Running against a real provider here would cost real money on every CI
// run for a result this test doesn't need - see docs/operations/ci-quality-gate-policy.md and
// the project's cost-conscious testing policy. So this test's "happy path" is the actual
// current behavior of the deployed app: evidence submission and a run reaching a real,
// deterministic terminal state (a validation failure) - not a fabricated success. It still
// exercises every UI transition (idle -> running -> completed) a successful run would.

test('create a project, submit evidence, and run a requirements analysis to a terminal state', async ({
  page,
}) => {
  await page.goto('/')
  await expect(page).toHaveURL(/\/projects$/)

  await page.getByRole('button', { name: 'Create Website Project' }).click()
  await expect(page).toHaveURL(/\/projects\/[^/]+$/)
  await expect(page.getByRole('heading', { name: 'website project' })).toBeVisible()

  // Scoped to the free-text section specifically - StructuredInputSection has its own,
  // identically-labeled "Add evidence" submit button.
  const freeTextSection = page.locator('section.input-section', { hasText: 'Free-text evidence' })
  const evidence = 'Acme Bakery sells sourdough bread and pastries in Vienna, open Mon-Sat 7am-6pm.'
  await freeTextSection.getByPlaceholder('Describe the business, in your own words…').fill(evidence)
  await freeTextSection.getByRole('button', { name: 'Add evidence' }).click()
  await expect(page.getByText(evidence)).toBeVisible()

  const startButton = page.getByRole('button', { name: 'Start Requirements Analysis' })
  await expect(startButton).toBeEnabled()
  await startButton.click()

  // No arbitrary sleep: the run is synchronous on the backend, so the button's own re-enabled,
  // relabeled state is the real signal a terminal state was reached.
  await expect(page.getByRole('button', { name: 'Start Requirements Analysis' })).toBeEnabled({
    timeout: 30_000,
  })
  await expect(page.getByText('Execution', { exact: false })).toBeVisible()
  await expect(page.getByText('mock / mock-model')).toBeVisible()
})
