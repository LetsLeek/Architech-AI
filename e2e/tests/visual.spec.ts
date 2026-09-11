import { expect, test } from '@playwright/test'

// AIW-102: a small, deliberately stable set of screens - visual tests don't replace behavioral/
// unit/accessibility coverage (critical-flow.spec.ts and accessibility.spec.ts already cover
// this same flow's actual behavior), they only catch unintended visual drift on top of that.
// Every test here carries the `@visual` tag so package.json can run this suite separately
// (`npm run test:visual`) from the required `npm test` - see e2e/README.md for why this stays
// a warning-only, non-required CI job while AIW-92's journey/a11y tests are required.

test('projects page matches visual baseline @visual', async ({ page }) => {
  await page.goto('/projects')
  await expect(page).toHaveScreenshot('projects-page.png')
})

test('project detail page matches visual baseline @visual', async ({ page }) => {
  await page.goto('/projects')
  await page.getByRole('button', { name: 'Create Website Project' }).click()
  await expect(page).toHaveURL(/\/projects\/[^/]+$/)
  await expect(page.getByRole('heading', { name: 'Requirements Analysis' })).toBeVisible()

  await expect(page).toHaveScreenshot('project-detail-page.png', {
    // Documented exception: the <dl> here shows this run's own random project ID and
    // creation timestamp - both genuinely different on every run, not a rendering
    // regression, so masking is the correct tool per AIW-102's own AC (masked only for
    // this specific, real reason - not applied blanket to the rest of the page).
    mask: [page.locator('dl')],
  })
})
