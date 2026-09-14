import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

test('built browser distribution includes original application license and notices', async () => {
  const notices = await readFile(new URL('../web/dist/THIRD_PARTY_NOTICES.txt', import.meta.url), 'utf8');
  for (const name of ['LICENSE', 'NOTICE']) {
    const original = await readFile(new URL(`../${name}`, import.meta.url), 'utf8');
    assert.ok(notices.includes(original.trim()), `Missing original ${name} in browser distribution`);
  }
});
