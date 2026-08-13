export function readSessionFilter<T extends Record<string, string>>(key: string, fallback: T): T {
  try {
    const raw = sessionStorage.getItem(key)
    if (!raw) return fallback
    const parsed: unknown = JSON.parse(raw)
    if (parsed == null || typeof parsed !== 'object' || Array.isArray(parsed)) return fallback
    return { ...fallback, ...(parsed as Record<string, string>) }
  } catch {
    return fallback
  }
}

export function writeSessionFilter(key: string, value: Record<string, string>) {
  try {
    sessionStorage.setItem(key, JSON.stringify(value))
  } catch {
    /* ignore quota / private mode */
  }
}
