import { describe, expect, it } from 'vitest'
import { router } from '../router'

describe('router', () => {
  it('defines app routes with layout and feature pages', () => {
    expect(router.routes.length).toBeGreaterThan(0)
    const root = router.routes[0]
    expect(root?.path).toBe('/')
    expect(root?.children?.length).toBeGreaterThan(0)
    const paths = root?.children?.map((child) => child.path) ?? []
    expect(paths).toContain('imports')
    expect(paths).toContain('products')
    expect(paths).toContain('sales')
    expect(paths).toContain('*')
  })
})
