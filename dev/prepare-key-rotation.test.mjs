import { test } from 'node:test'
import assert from 'node:assert/strict'
import { generateKeyPairSync, createPrivateKey, createPublicKey } from 'node:crypto'
import { mkdtemp, writeFile, readFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { execFileSync } from 'node:child_process'

test('preparation preserves active key, retains public history and refuses overwrite', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'campus-rotation-'))
  try {
    const old = generateKeyPairSync('ed25519').privateKey.export({ format: 'jwk' })
    const active = join(directory, 'active.jwk')
    await writeFile(active, JSON.stringify(old))
    const next = join(directory, 'next')
    const run = (...args) => execFileSync(process.execPath, ['dev/prepare-key-rotation.mjs', ...args], { stdio: 'pipe' })
    run(active, next)
    assert.deepEqual(JSON.parse(await readFile(active)), old)
    const newPrivate = JSON.parse(await readFile(join(next, 'campus-signing.jwk')))
    assert.notEqual(newPrivate.x, old.x)
    assert.equal(createPublicKey(createPrivateKey({ key: newPrivate, format: 'jwk' })).export({ format: 'jwk' }).x, newPrivate.x)
    const history = JSON.parse(await readFile(join(next, 'campus-verification.jwks')))
    assert.deepEqual(new Set(history.keys.map(key => key.x)), new Set([old.x, newPrivate.x]))
    assert.ok(history.keys.every(key => !('d' in key)))
    assert.throws(() => run(active, next))
    assert.deepEqual(JSON.parse(await readFile(join(next, 'campus-signing.jwk'))), newPrivate)
    const third = join(directory, 'third')
    run(join(next, 'campus-signing.jwk'), third, join(next, 'campus-verification.jwks'))
    assert.equal(JSON.parse(await readFile(join(third, 'campus-verification.jwks'))).keys.length, 3)
    const unsafe = join(directory, 'private-history.json')
    await writeFile(unsafe, JSON.stringify({ keys: [old] }))
    assert.throws(() => run(active, join(directory, 'unsafe'), unsafe))
  } finally { await rm(directory, { recursive: true, force: true }) }
})
