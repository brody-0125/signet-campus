import { test } from 'node:test'
import assert from 'node:assert/strict'
import https from 'node:https'
import { readFileSync } from 'node:fs'
import { execFileSync } from 'node:child_process'

const origin = 'https://localhost:8443'
const ca = readFileSync(process.env.CAMPUS_TLS_CERT)
const get = (path, options = {}) => new Promise((resolve, reject) => {
  https.get(`${origin}${path}`, { ca, ...options }, response => {
    const protocol = response.socket.getProtocol()
    let body = ''
    response.on('data', chunk => { body += chunk })
    response.on('end', () => resolve({ status: response.statusCode, body, headers: response.headers, protocol }))
  }).on('error', reject)
})

test('only the HTTPS ingress is published to the host', () => {
  const services = ['db', 'server', 'identity', 'web']
  const containers = JSON.parse(execFileSync('docker', ['inspect', ...services.map(service => `signet-campus-tls-${service}-1`)], { encoding: 'utf8' }))
  for (const container of containers.slice(0, 3)) assert.deepEqual(container.HostConfig.PortBindings ?? {}, {})
  assert.deepEqual(containers[3].HostConfig.PortBindings, { '8443/tcp': [{ HostIp: '127.0.0.1', HostPort: '8443' }] })
})

test('TLS 1.2 and 1.3 serve the web and status APIs with certificate validation', async () => {
  for (const version of ['TLSv1.2', 'TLSv1.3']) {
    const response = await get('/api/revocations', { minVersion: version, maxVersion: version })
    assert.equal(response.status, 200)
    assert.equal(response.protocol, version)
    assert.equal(JSON.parse(response.body).id, `${origin}/api/revocations`)
  }
  const page = await get('/')
  assert.equal(page.status, 200)
  assert.match(page.body, /<html/)
  const discovery = JSON.parse((await get('/auth/realms/signet-campus/.well-known/openid-configuration')).body)
  assert.equal(discovery.issuer, `${origin}/auth/realms/signet-campus`)
  assert.ok(discovery.authorization_endpoint.startsWith(`${origin}/auth/`))
  const login = await get('/auth/realms/signet-campus/protocol/openid-connect/auth?' + new URLSearchParams({
    client_id: 'campus-dev', redirect_uri: origin, response_type: 'code', scope: 'openid',
    code_challenge: 'abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG', code_challenge_method: 'S256',
  }))
  assert.equal(login.status, 200)
  assert.match(login.body, /action="https:\/\/localhost:8443\/auth\//)
  assert.ok(login.headers['set-cookie'].some(cookie => cookie.startsWith('AUTH_SESSION_ID=') && cookie.includes('Secure')))
})

test('untrusted certificate, wrong hostname and legacy TLS are rejected', async () => {
  await assert.rejects(get('/', { ca: [] }), { code: 'DEPTH_ZERO_SELF_SIGNED_CERT' })
  await assert.rejects(get('/', { servername: 'wrong.example.test' }), { code: 'ERR_TLS_CERT_ALTNAME_INVALID' })
  await assert.rejects(get('/', { minVersion: 'TLSv1.1', maxVersion: 'TLSv1.1', ciphers: 'DEFAULT:@SECLEVEL=0' }),
    error => error.code === 'EPROTO' && /alert protocol version/i.test(error.message))
})
