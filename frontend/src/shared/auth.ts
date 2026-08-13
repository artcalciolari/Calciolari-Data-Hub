const AUTH_STORAGE_KEY = 'datahub.basic'

export function readBasicAuth(): string | null {
  try {
    return sessionStorage.getItem(AUTH_STORAGE_KEY)
  } catch {
    return null
  }
}

export function setBasicAuth(username: string, password: string) {
  sessionStorage.setItem(AUTH_STORAGE_KEY, btoa(`${username}:${password}`))
}

export function clearBasicAuth() {
  try {
    sessionStorage.removeItem(AUTH_STORAGE_KEY)
  } catch {
    /* ignore */
  }
}
