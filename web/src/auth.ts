import Keycloak from 'keycloak-js'
import { create } from 'zustand'

const keycloak = new Keycloak({
  url: import.meta.env.VITE_OIDC_URL || 'http://localhost:8081',
  realm: import.meta.env.VITE_OIDC_REALM || 'signet-campus',
  clientId: import.meta.env.VITE_OIDC_CLIENT || 'campus-dev',
})
export const useSession = create(() => ({ ready: false, authenticated: false, reviewer: false, error: '' }))
const sync = () => useSession.setState({
  authenticated: !!keycloak.authenticated,
  reviewer: keycloak.hasRealmRole('reviewer'),
})
export async function initializeSession() {
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
  try { await keycloak.login({ redirectUri: window.location.origin }) }
  catch { useSession.setState({ error: 'Unable to open sign-in. Please reload to try again.' }) }
}
export async function signOut() {
  try { await keycloak.logout({ redirectUri: window.location.origin }) }
  catch { useSession.setState({ error: 'Unable to sign out. Please try again.' }) }
}
export async function accessToken() {
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
