import { afterEach, describe, expect, it, vi } from 'vitest'

const { init, login, createClient } = vi.hoisted(() => ({ init: vi.fn(), login: vi.fn(), createClient: vi.fn() }))
vi.mock('keycloak-js', () => ({ default: class {
  constructor(config: unknown) { createClient(config) }
  init = init
  login = login
  hasRealmRole = () => false
} }))
afterEach(() => { vi.unstubAllEnvs(); vi.resetModules(); vi.clearAllMocks() })

describe('deployment authentication', () => {
  it('does not contact localhost or redirect when production has no identity provider', async () => {
    vi.stubEnv('DEV', false)
    vi.stubEnv('VITE_OIDC_URL', '')
    const auth = await import('./auth')
    await auth.initializeSession()
    await auth.signIn()
    expect(createClient).not.toHaveBeenCalled()
    expect(init).not.toHaveBeenCalled()
    expect(login).not.toHaveBeenCalled()
    expect(auth.useSession.getState()).toMatchObject({ ready: true, authenticated: false, error: '', notice: 'Sign-in is not available on this deployment.' })
    await expect(auth.accessToken()).rejects.toThrow('Sign-in is not available')
  })
  it('keeps the local development identity provider', async () => {
    vi.stubEnv('DEV', true)
    vi.stubEnv('VITE_OIDC_URL', '')
    const auth = await import('./auth')
    await auth.initializeSession()
    expect(createClient).toHaveBeenCalledWith(expect.objectContaining({ url: 'http://localhost:8081' }))
    expect(init).toHaveBeenCalledWith(expect.objectContaining({ onLoad: 'check-sso', pkceMethod: 'S256' }))
  })
  it('uses an explicitly configured provider in production', async () => {
    vi.stubEnv('DEV', false)
    vi.stubEnv('VITE_OIDC_URL', 'https://identity.example.test')
    vi.stubEnv('VITE_OIDC_REALM', 'campus')
    vi.stubEnv('VITE_OIDC_CLIENT', 'campus-web')
    const auth = await import('./auth')
    await auth.initializeSession()
    await auth.signIn()
    expect(createClient).toHaveBeenCalledWith({ url: 'https://identity.example.test', realm: 'campus', clientId: 'campus-web' })
    expect(login).toHaveBeenCalledWith({ redirectUri: window.location.origin })
  })
})
