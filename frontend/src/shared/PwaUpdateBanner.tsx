import { useEffect, useRef } from 'react'
import { useRegisterSW } from 'virtual:pwa-register/react'

/**
 * Banner shown when a new service worker is waiting.
 * Reloads the page to activate the new app shell — never caches API data.
 */
export function PwaUpdateBanner() {
  const intervalRef = useRef<number | null>(null)
  const {
    needRefresh: [needRefresh, setNeedRefresh],
    updateServiceWorker,
  } = useRegisterSW({
    onRegisteredSW(_swUrl, registration) {
      if (registration) {
        intervalRef.current = window.setInterval(() => {
          void registration.update()
        }, 60 * 60 * 1000)
      }
    },
  })

  useEffect(() => () => {
    if (intervalRef.current != null) {
      window.clearInterval(intervalRef.current)
    }
  }, [])

  if (!needRefresh) return null

  return (
    <div className="pwa-banner" role="status">
      <span>Nova versão disponível.</span>
      <button
        type="button"
        className="btn primary"
        onClick={() => {
          void updateServiceWorker(true)
        }}
      >
        Atualizar
      </button>
      <button
        type="button"
        className="btn secondary"
        onClick={() => setNeedRefresh(false)}
      >
        Depois
      </button>
    </div>
  )
}
