import { NavLink, Outlet } from 'react-router-dom'
import { NAV_ITEMS } from '@/shared/api'
import { Icon } from '@/shared/icons'
import { PwaUpdateBanner } from '@/shared/PwaUpdateBanner'

export function AppLayout() {
  return (
    <div className="app-shell">
      <PwaUpdateBanner />
      <header className="app-header">
        <NavLink to="/" className="brand-block" end aria-label="Calciolari Data Hub — início">
          <img
            className="brand-logo"
            src="/logo-calciolari-header.png"
            alt="Calciolari Cucina Italiana"
            width={220}
            height={54}
          />
        </NavLink>
        <nav className="top-nav" aria-label="Navegação principal">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}
            >
              <Icon name={item.icon} size={18} />
              {item.label}
            </NavLink>
          ))}
        </nav>
      </header>
      <main className="app-main">
        <Outlet />
      </main>
      <nav className="bottom-nav" aria-label="Navegação inferior">
        {NAV_ITEMS.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/'}
            className={({ isActive }) => (isActive ? 'bottom-link active' : 'bottom-link')}
          >
            <Icon name={item.icon} size={20} />
            <span>{item.label}</span>
          </NavLink>
        ))}
      </nav>
    </div>
  )
}
