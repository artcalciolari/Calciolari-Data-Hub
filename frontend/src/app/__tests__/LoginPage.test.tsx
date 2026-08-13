import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { LoginPage } from '../LoginPage'

vi.mock('@/shared/api', () => ({
  listProducts: vi.fn(),
}))

import { listProducts } from '@/shared/api'
import { readBasicAuth } from '@/shared/auth'

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/" element={<div>home</div>} />
      </Routes>
    </MemoryRouter>
  )
}

describe('LoginPage', () => {
  beforeEach(() => {
    sessionStorage.clear()
    vi.mocked(listProducts).mockReset()
  })

  afterEach(() => {
    sessionStorage.clear()
  })

  it('signs in and navigates home', async () => {
    vi.mocked(listProducts).mockResolvedValue({
      content: [],
      page: 0,
      size: 1,
      totalElements: 0,
      totalPages: 0,
    })
    renderPage()
    fireEvent.change(screen.getByLabelText('Usuário'), { target: { value: 'admin' } })
    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'secret' } })
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }))
    expect(await screen.findByText('home')).toBeInTheDocument()
    expect(readBasicAuth()).toBe(btoa('admin:secret'))
  })

  it('keeps submit disabled without credentials', () => {
    renderPage()
    expect(screen.getByRole('button', { name: 'Entrar' })).toBeDisabled()
  })

  it('shows error and clears credentials on failure', async () => {
    vi.mocked(listProducts).mockRejectedValue(new Error('nope'))
    renderPage()
    fireEvent.change(screen.getByLabelText('Usuário'), { target: { value: 'admin' } })
    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'bad' } })
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }))
    expect(await screen.findByText('Usuário ou senha inválidos')).toBeInTheDocument()
    expect(readBasicAuth()).toBeNull()
    await waitFor(() => expect(screen.getByRole('button', { name: 'Entrar' })).not.toBeDisabled())
  })

  it('shows a busy label while signing in', async () => {
    vi.mocked(listProducts).mockImplementation(() => new Promise(() => {}))
    renderPage()
    fireEvent.change(screen.getByLabelText('Usuário'), { target: { value: 'admin' } })
    fireEvent.change(screen.getByLabelText('Senha'), { target: { value: 'secret' } })
    fireEvent.click(screen.getByRole('button', { name: 'Entrar' }))
    expect(await screen.findByRole('button', { name: 'Entrando…' })).toBeDisabled()
  })
})
