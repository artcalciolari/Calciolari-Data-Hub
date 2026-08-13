import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { listProducts } from '@/shared/api'
import { clearBasicAuth, setBasicAuth } from '@/shared/auth'

export function LoginPage() {
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    setBasicAuth(username, password)
    try {
      await listProducts({ size: 1 })
      navigate('/', { replace: true })
    } catch {
      clearBasicAuth()
      setError('Usuário ou senha inválidos')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="login-shell">
      <form className="login-card" onSubmit={submit}>
        <h1>Entrar</h1>
        <p className="muted">Use a conta configurada no servidor (HTTP Basic).</p>
        <label>
          Usuário
          <input
            autoComplete="username"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            required
          />
        </label>
        <label>
          Senha
          <input
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
          />
        </label>
        {error && <p className="notice error">{error}</p>}
        <button className="btn primary" type="submit" disabled={busy || !username || !password}>
          {busy ? 'Entrando…' : 'Entrar'}
        </button>
      </form>
    </div>
  )
}
