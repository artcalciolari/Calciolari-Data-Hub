import { NavLink, Outlet } from 'react-router-dom'
import { NAV_ITEMS } from '@/shared/api'
import { Icon } from '@/shared/icons'
import { PwaUpdateBanner } from '@/shared/PwaUpdateBanner'

export function AppLayout() {
  return (
    <div className="app-shell">
      <PwaUpdateBanner />
      <header className="app-header">
        <div>
          <p className="brand">Calciolari Data Hub</p>
          <p className="brand-sub">Auditoria e indicadores InterPDV</p>
        </div>
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
