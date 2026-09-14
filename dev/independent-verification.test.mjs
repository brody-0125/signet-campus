import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';

for (const name of ['Keyboard audit', 'Cafe\u0301 \u1100\u1161 accessibility']) {
  test(`independent verification preserves ${name}`, { timeout: 120000 }, () => {
    const result = spawnSync(process.execPath, ['dev/smoke.mjs'], {
      env: { ...process.env, CAMPUS_INDEPENDENT_VERIFY: 'true', CAMPUS_SMOKE_NAME: name }, encoding: 'utf8', timeout: 110000,
    });
    assert.equal(result.status, 0, result.stderr);
    console.log(result.stdout.trim());
  });
}
