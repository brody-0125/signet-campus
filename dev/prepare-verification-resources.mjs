import assert from 'node:assert/strict';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { createHash } from 'node:crypto';

const manifest = JSON.parse(await readFile(new URL('./verification-resources.json', import.meta.url)));
const directory = new URL('../secrets/verification-resources/', import.meta.url);
await mkdir(directory, { recursive: true });
for (const { file, url, sha256 } of manifest) {
  assert.match(file, /^[a-z0-9-]+\.json$/);
  const response = await fetch(url, { headers: { Accept: 'application/ld+json, application/json' }, signal: AbortSignal.timeout(30000) });
  assert.equal(response.status, 200, `Cannot retrieve ${url}`);
  const bytes = Buffer.from(await response.arrayBuffer());
  assert.equal(createHash('sha256').update(bytes).digest('hex'), sha256, `Upstream resource changed: ${url}`);
  JSON.parse(bytes);
  await writeFile(new URL(file, directory), bytes);
}
console.log('Official verification resources downloaded and SHA-256 checked');
