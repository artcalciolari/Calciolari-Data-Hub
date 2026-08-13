import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import AxeBuilder from '@axe-core/playwright'
import { expect, test, type APIRequestContext } from '@playwright/test'

const repoRoot = join(dirname(fileURLToPath(import.meta.url)), '../..')
const fixtureB = join(
  repoRoot,
  'backend/tests/Calciolari.DataHub.Tests/fixtures/qrp/fixture-b.qrp',
)

async function findSaleId(request: APIRequestContext, externalSaleId: string) {
  for (let page = 0; page < 20; page += 1) {
    const response = await request.get(`/api/sales?page=${page}&size=50`)
    expect(response.ok()).toBeTruthy()
    const body = (await response.json()) as {
      content: { id: string; externalSaleId: string }[]
      totalPages: number
    }
    const hit = body.content.find((sale) => sale.externalSaleId === externalSaleId)
    if (hit) return hit.id
    if (page + 1 >= body.totalPages) break
  }
  throw new Error(`sale ${externalSaleId} not found`)
}

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

  test('dashboard has no serious or critical axe violations', async ({ page }) => {
    test.skip(test.info().project.name !== 'desktop', 'axe audit on desktop viewport')
    await page.goto('/')
    await expect(page.getByRole('heading', { name: 'Resumo' })).toBeVisible()
    const results = await new AxeBuilder({ page }).analyze()
    const blocking = results.violations.filter(
      (violation) => violation.impact === 'serious' || violation.impact === 'critical',
    )
    expect(blocking, JSON.stringify(blocking, null, 2)).toEqual([])
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

  test('sale 134409 shows fixture B item values', async ({ page, request }) => {
    const id = await findSaleId(request, '134409')
    await page.goto(`/sales/${id}`)
    await expect(page.getByRole('heading', { name: 'Venda 134409' })).toBeVisible()
    await expect(page.getByText(/0,416/)).toBeVisible()
    await expect(page.getByText(/56,90/)).toBeVisible()
    await expect(page.getByText(/desc\. 8%/)).toBeVisible()
    await expect(page.getByText(/21,78/)).toBeVisible()
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

  test('import file detail shows structured validations', async ({ page }) => {
    await page.goto('/imports')
    await page.locator('tbody tr').first().click()
    await expect(page.getByRole('heading', { name: 'Arquivos' })).toBeVisible()
    await page.locator('tbody tr').first().click()
    await expect(page.getByRole('heading', { name: 'Validações' })).toBeVisible()
    await expect(page.getByText('SOURCE_QUANTITY_MATCH')).toBeVisible()
    await expect(page.getByText('Válido').first()).toBeVisible()
  })

  test('upload of fixture B reports a duplicate of existing content', async ({ page }) => {
    await page.goto('/imports')
    await page.locator('input[type="file"]').setInputFiles({
      name: 'AUDITORIA 41, 01_07-20_07.QRP',
      mimeType: 'application/octet-stream',
      buffer: readFileSync(fixtureB),
    })
    await page.getByRole('button', { name: /Importar \(1\)/ }).click()
    await expect(page.getByText('Já importado (mesmo conteúdo)')).toBeVisible({ timeout: 60_000 })
    await expect(page.getByText(/MOLHO POMODORO|AUDITORIA 41/)).toBeVisible()
  })

  test('unknown route renders the not-found page', async ({ page }) => {
    await page.goto('/pagina-inexistente')
    await expect(page.getByRole('heading', { name: 'Página não encontrada' })).toBeVisible()
    await expect(page.getByRole('link', { name: 'Ir para Resumo' })).toBeVisible()
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
