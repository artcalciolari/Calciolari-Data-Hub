import { expect, test } from '@playwright/test'

test.describe('Calciolari Data Hub (seeded backend)', () => {
  test('dashboard shows published totals and rankings', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByRole('heading', { name: 'Resumo' })).toBeVisible()
    await expect(page.getByText('Faturamento no período')).toBeVisible()
    await expect(page.getByText(/3\.705,88/)).toBeVisible()
    await expect(page.getByText(/63,828/)).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Evolução diária' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Top produtos' })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Vendas recentes' })).toBeVisible()
    await page.getByRole('button', { name: 'Qtd' }).click()
    await expect(page.getByRole('button', { name: 'Qtd' })).toHaveClass(/active/)
    const navName = test.info().project.name === 'mobile' ? 'Navegação inferior' : 'Navegação principal'
    await expect(page.getByRole('navigation', { name: navName })).toBeVisible()
  })

  test('sales list and sale detail render items', async ({ page }) => {
    await page.goto('/sales')
    await expect(page.getByRole('heading', { name: 'Vendas', exact: true })).toBeVisible()
    const firstSale = page.locator('tbody tr').first()
    await expect(firstSale).toBeVisible()
    await firstSale.click()
    await expect(page.getByRole('heading', { name: /Venda \d+/ })).toBeVisible()
    await expect(page.getByRole('heading', { name: 'Itens' })).toBeVisible()
  })

  test('product page shows daily bars', async ({ page }) => {
    await page.goto('/products')
    await expect(page.getByRole('heading', { name: 'Produtos', exact: true })).toBeVisible()
    await page.getByText('MOLHO POMODORO').click()
    await expect(page.getByRole('heading', { name: 'MOLHO POMODORO' })).toBeVisible()
    await expect(page.getByRole('img', { name: 'Série diária do produto' })).toBeVisible()
  })

  test('imports page shows upload dropzone and history', async ({ page }) => {
    await page.goto('/imports')
    await expect(page.getByRole('heading', { name: 'Importar', exact: true })).toBeVisible()
    await expect(page.getByText('Arraste os .QRP aqui')).toBeVisible()
    await expect(page.locator('tbody tr').first()).toBeVisible()
    const firstJob = page.locator('tbody tr').first()
    await firstJob.click()
    await expect(page.getByRole('heading', { name: /AUDITORIA|Importação/ })).toBeVisible()
    await expect(page.getByText(/AUDITORIA 41, 01_07-20_07\.QRP|AUDITORIA\.QRP/).first()).toBeVisible()
  })

  test('PWA manifest is linked and installable metadata is present', async ({ page }) => {
    await page.goto('/')
    const manifestHref = await page.locator('link[rel="manifest"]').getAttribute('href')
    expect(manifestHref).toBeTruthy()
    const manifest = await page.request.get(manifestHref!)
    expect(manifest.ok()).toBeTruthy()
    const body = await manifest.json()
    expect(body.name).toBe('Calciolari Data Hub')
    expect(body.display).toBe('standalone')
    expect(body.theme_color).toBe('#2D2823')
    expect(body.icons?.length).toBeGreaterThanOrEqual(2)
  })
})
