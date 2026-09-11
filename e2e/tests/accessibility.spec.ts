import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page, type TestInfo } from '@playwright/test'

// AIW-98: automated checks here catch common, mechanically-detectable issues (missing labels,
// bad contrast, invalid ARIA, ...) - they do NOT replace manual keyboard navigation or
// screen-reader review, which axe-core itself cannot perform. Treat a clean run here as a
// floor, not a substitute for that manual pass.

async function expectNoSeriousOrCriticalViolations(page: Page, testInfo: TestInfo) {
  const { violations } = await new AxeBuilder({ page }).analyze()

  // Every violation is attached in full (any impact level) so moderate/minor issues stay
  // visible for triage without blocking the build over them - matching AIW-87's
  // severity-tiering policy applied to axe's own impact scale.
  await testInfo.attach('axe-violations', {
    body: JSON.stringify(violations, null, 2),
    contentType: 'application/json',
  })

  const severe = violations.filter((v) => v.impact === 'serious' || v.impact === 'critical')
  expect(severe, JSON.stringify(severe, null, 2)).toEqual([])
}

test('projects page has no serious/critical accessibility violations', async ({ page }, testInfo) => {
  await page.goto('/projects')
  await expectNoSeriousOrCriticalViolations(page, testInfo)
})

test('project detail page has no serious/critical accessibility violations', async ({ page }, testInfo) => {
  await page.goto('/projects')
  await page.getByRole('button', { name: 'Create Website Project' }).click()
  await expect(page).toHaveURL(/\/projects\/[^/]+$/)
  // Waits for every input section (free-text/structured/file) and the requirements-analysis
  // section to actually render before scanning - otherwise axe would only see a partial page.
  await expect(page.getByRole('heading', { name: 'Requirements Analysis' })).toBeVisible()

  await expectNoSeriousOrCriticalViolations(page, testInfo)
})
