import { generateKeyPairSync } from 'node:crypto'
import { mkdir, open, chown } from 'node:fs/promises'
import { dirname } from 'node:path'

const path = process.argv[2]
if (!path) throw new Error('Provide the private JWK file path.')
await mkdir(dirname(path), { recursive: true, mode: 0o700 })
try {
  const file = await open(path, 'wx', 0o600)
  try {
    const { privateKey } = generateKeyPairSync('ed25519')
    await file.writeFile(JSON.stringify(privateKey.export({ format: 'jwk' })))
    await file.sync()
    if (process.getuid?.() === 0) await file.chown(10001, 10001)
  } finally { await file.close() }
} catch (error) { if (error.code !== 'EEXIST') throw error }
if (process.getuid?.() === 0) await chown(dirname(path), 10001, 10001)
console.log('Signing key ready. Existing keys are preserved.')
