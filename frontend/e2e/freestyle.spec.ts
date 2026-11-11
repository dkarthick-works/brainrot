import { expect, test, type Page } from '@playwright/test'

const card = (id: number) => ({ id: String(id), topicId: 'topic', question: `Question ${id}\n${'Long content that remains scrollable.\n'.repeat(30)}`, answer: `Answer ${id}\n${'More answer content.\n'.repeat(30)}`, easeFactor: 2.5, interval: 30, repetitions: 3, dueAt: '2099-01-01T00:00:00Z', lastReviewedAt: null, createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' })
async function swipe(page: Page, direction = 1) {
  const top = page.locator('.freestyle-stack .card').first()
  const box = (await top.boundingBox())!
  const x = box.x + box.width / 2, y = box.y + box.height / 2
  await page.mouse.move(x, y)
  await page.mouse.down()
  await page.mouse.move(x + direction * 125, y, { steps: 5 })
  await page.mouse.up()
}
async function api(page: Page, cards = [card(1)]) {
  const writes: string[] = []
  await page.route('**/api/**', async route => {
    if (route.request().method() !== 'GET') writes.push(route.request().url())
    const path = new URL(route.request().url()).pathname
    await route.fulfill({ json: path.endsWith('/topics') ? [{ id: 'topic', name: 'A very long topic name '.repeat(10), cardCount: cards.length, dueCount: 0 }]
      : path.endsWith('/due') ? { cards: [], dueCount: 0, nextDueAt: cards[0]?.dueAt ?? null }
        : { cards, nextCursor: null } })
  })
  return writes
}

for (const theme of ['light', 'dark']) {
  for (const viewport of [{ width: 390, height: 844 }, { width: 320, height: 568 }, { width: 667, height: 375 }, { width: 1280, height: 900 }]) {
    test(`${theme} ${viewport.width}×${viewport.height}: scroll, flip, drawer, swipe, replay and exit`, async ({ page }) => {
      await page.setViewportSize(viewport)
      const writes = await api(page)
      await page.goto('/freestyle')
      await page.evaluate(theme => document.documentElement.dataset.theme = theme, theme)
      const exit = page.getByRole('button', { name: 'Exit Freestyle' })
      await expect(exit).toBeVisible()
      await expect(page.getByRole('status')).toHaveText('Card 1')
      const titleBox = (await page.getByRole('heading', { name: 'Freestyle', exact: true }).boundingBox())!
      const exitBox = (await exit.boundingBox())!
      expect(titleBox.x + titleBox.width <= exitBox.x || titleBox.y + titleBox.height <= exitBox.y).toBeTruthy()
      const content = page.locator('.freestyle-stack .front .card-content').first()
      expect(await content.evaluate(el => el.scrollHeight > el.clientHeight)).toBeTruthy()
      await content.hover()
      await page.mouse.wheel(0, 150)
      await expect.poll(() => content.evaluate(el => el.scrollTop)).toBeGreaterThan(0)
      await page.getByRole('button', { name: 'Tap to reveal answer' }).press('Enter')
      await expect(page.getByRole('button', { name: 'Answer shown. Tap to show question' })).toBeVisible()
      await page.locator('.freestyle-stack .card-inner').first().evaluate(async el => {
        await Promise.all(el.getAnimations().map(animation => animation.finished))
      })
      const answer = page.locator('.freestyle-stack .back .card-content').first()
      await answer.hover()
      await page.mouse.wheel(0, 150)
      await expect.poll(() => answer.evaluate(el => el.scrollTop)).toBeGreaterThan(0)
      await page.getByRole('button', { name: 'Open menu' }).click()
      await expect(page.getByRole('button', { name: 'Freestyle', exact: true })).toHaveAttribute('aria-current', 'page')
      await page.getByRole('button', { name: 'Freestyle', exact: true }).click()
      await expect(page.getByRole('button', { name: 'Answer shown. Tap to show question' })).toBeVisible()
      await swipe(page)
      await expect(page.getByRole('heading', { name: 'Freestyle complete' })).toBeVisible()
      await page.getByRole('button', { name: 'Play again' }).click()
      await expect(page.getByRole('button', { name: 'Tap to reveal answer' })).toBeVisible()
      await exit.click()
      await expect(page.getByRole('heading', { name: 'Brain Upgraded' })).toBeVisible()
      await page.getByRole('button', { name: 'Freestyle', exact: true }).click()
      await expect(page.getByRole('status')).toHaveText('Card 1')
      await page.goBack()
      await page.goForward()
      await expect(page.getByRole('status')).toHaveText('Card 1')
      expect(writes).toEqual([])
    })
  }
}

test('102 cards: page-local dedupe, later read failure/retry, reduced motion, one pass and no writes', async ({ page }) => {
  test.setTimeout(120_000)
  await page.emulateMedia({ reducedMotion: 'reduce' })
  let failed = false
  const cursors: string[] = [], writes: string[] = []
  await page.route('**/api/**', async route => {
    if (route.request().method() !== 'GET') writes.push(route.request().url())
    const url = new URL(route.request().url())
    if (url.pathname.endsWith('/topics')) return route.fulfill({ json: [] })
    const cursor = url.searchParams.get('after') || ''
    cursors.push(cursor)
    if (cursor === '50' && !failed) { failed = true; return route.fulfill({ status: 500, json: {} }) }
    const start = Number(cursor)
    const cards = Array.from({ length: Math.min(50, 102 - start) }, (_, i) => card(start + i))
    if (start) cards.push(card(start - 1))
    await route.fulfill({ json: { cards, nextCursor: start < 100 ? String(start + 50) : null } })
  })
  await page.goto('/freestyle')
  for (let i = 1; i <= 102; i++) {
    await expect(page.getByRole('status')).toHaveText(`Card ${i}`)
    if (i === 46) {
      await expect(page.getByRole('alert')).toContainText('Something broke on our end')
      await page.getByRole('button', { name: 'Try again' }).click()
    }
    await swipe(page, i % 2 ? 1 : -1)
  }
  await expect(page.getByRole('heading', { name: 'Freestyle complete' })).toBeVisible()
  expect(cursors.filter(c => c === '50')).toHaveLength(2)
  expect(cursors.filter(c => c === '100')).toHaveLength(1)
  expect(writes).toEqual([])
})
