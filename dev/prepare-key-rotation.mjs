import { createPrivateKey, createPublicKey, generateKeyPairSync } from 'node:crypto'
import { readFile, mkdir, open, chown } from 'node:fs/promises'
import { join } from 'node:path'

const [activePath, destination, historyPath] = process.argv.slice(2)
if (!activePath || !destination) throw new Error('Usage: prepare-key-rotation.mjs ACTIVE_JWK NEW_DIRECTORY [PUBLIC_JWKS]')
const active = createPrivateKey({ key: JSON.parse(await readFile(activePath)), format: 'jwk' })
if (active.asymmetricKeyType !== 'ed25519') throw new Error('An Ed25519 signing key is required.')
const history = historyPath ? JSON.parse(await readFile(historyPath)).keys : []
if (!Array.isArray(history)) throw new Error('Public history must be a JWK Set.')
const publicKeys = history.map(key => {
  if ('d' in key || key.kty !== 'OKP' || key.crv !== 'Ed25519') throw new Error('History must contain only Ed25519 public keys.')
  return createPublicKey({ key, format: 'jwk' }).export({ format: 'jwk' })
})
const next = generateKeyPairSync('ed25519')
publicKeys.push(createPublicKey(active).export({ format: 'jwk' }), next.publicKey.export({ format: 'jwk' }))
const keys = [...new Map(publicKeys.map(key => [key.x, key])).values()]
// An existing destination is rejected, including a directory from an interrupted attempt.
await mkdir(destination, { mode: 0o700 })
for (const [name, value] of Object.entries({
  'campus-signing.jwk': next.privateKey.export({ format: 'jwk' }),
  'campus-verification.jwks': { keys },
})) {
  const file = await open(join(destination, name), 'wx', 0o600)
  try {
    await file.writeFile(JSON.stringify(value))
    await file.sync()
    if (process.getuid?.() === 0) await file.chown(10001, 10001)
  } finally { await file.close() }
}
if (process.getuid?.() === 0) await chown(destination, 10001, 10001)
console.log('Rotation prepared. Active files are unchanged; deploy the public set before activating the new signing key.')
