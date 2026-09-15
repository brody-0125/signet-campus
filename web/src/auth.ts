import Keycloak from 'keycloak-js'
import { create } from 'zustand'

const oidcUrl = import.meta.env.VITE_OIDC_URL || (import.meta.env.DEV ? 'http://localhost:8081' : '')
const unavailable = 'Sign-in is not available on this deployment.'
const keycloak = oidcUrl ? new Keycloak({
  url: oidcUrl,
  realm: import.meta.env.VITE_OIDC_REALM || 'signet-campus',
  clientId: import.meta.env.VITE_OIDC_CLIENT || 'campus-dev',
}) : null
export const useSession = create(() => ({ ready: false, authenticated: false, reviewer: false, error: '', notice: '' }))
const sync = () => useSession.setState({
  authenticated: !!keycloak?.authenticated,
  reviewer: keycloak?.hasRealmRole('reviewer') ?? false,
})
export async function initializeSession() {
  if (!keycloak) {
    useSession.setState({ ready: true, notice: unavailable })
    return
  }
  try {
    await keycloak.init({ onLoad: 'check-sso', pkceMethod: 'S256', checkLoginIframe: false })
    sync()
    keycloak.onAuthLogout = sync
    keycloak.onAuthRefreshError = () => { keycloak.clearToken(); sync() }
  } catch {
    useSession.setState({ error: 'Sign-in is temporarily unavailable. Please reload to try again.' })
  } finally { useSession.setState({ ready: true }) }
}
export async function signIn() {
  if (!keycloak) { useSession.setState({ notice: unavailable }); return }
  try { await keycloak.login({ redirectUri: window.location.origin }) }
  catch { useSession.setState({ error: 'Unable to open sign-in. Please reload to try again.' }) }
}
export async function signOut() {
  if (!keycloak) return
  try { await keycloak.logout({ redirectUri: window.location.origin }) }
  catch { useSession.setState({ error: 'Unable to sign out. Please try again.' }) }
}
export function manageAccount() { return keycloak?.accountManagement() }
export async function accessToken() {
  if (!keycloak) throw new Error(unavailable)
  try {
    await keycloak.updateToken(30)
    if (!keycloak.token) throw new Error('No token')
    return keycloak.token
  } catch {
    keycloak.clearToken()
    sync()
    throw new Error('Your session has expired. Sign in again to continue.')
  }
}
