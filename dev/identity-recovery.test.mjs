import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { setTimeout } from 'node:timers/promises';

const project = 'signet-campus-identity-recovery';
const compose = ['compose', '-p', project, '-f', 'compose.yml', '-f', 'compose.accounts.yml', '-f', 'compose.identity-recovery.yml'];
const issuer = 'http://localhost:8084/realms/signet-campus';
function docker(args, input, succeeds = true) {
  const result = spawnSync('docker', args, { input, timeout: 120000, maxBuffer: 64 * 1024 * 1024 });
  // Database archives contain identity secrets; never include subprocess output in failures.
  assert.equal(result.error, undefined, 'Docker command could not complete');
  if (succeeds) assert.equal(result.status, 0, 'Docker command failed (output withheld)');
  else assert.notEqual(result.status, 0, 'Unsafe or invalid restore unexpectedly succeeded');
  return result.stdout;
}
function container(service) {
  const id = docker([...compose, 'ps', '-q', service]).toString().trim();
  assert.match(id, /^[a-f0-9]{64}$/);
  const [info] = JSON.parse(docker(['inspect', id]));
  assert.equal(info.Config.Labels['com.docker.compose.project'], project);
  assert.equal(info.Config.Labels['com.docker.compose.service'], service);
  return id;
}
async function login(username) {
  const response = await fetch(`${issuer}/protocol/openid-connect/token`, { method: 'POST', body: new URLSearchParams({
    grant_type: 'password', client_id: 'campus-dev', username, password: `local-${username}-only`,
  }), signal: AbortSignal.timeout(10000) });
  assert.equal(response.status, 200);
  return (await response.json()).access_token;
}
const claims = token => JSON.parse(Buffer.from(token.split('.')[1], 'base64url'));
async function api(path, token, body) {
  const response = await fetch(`http://localhost:8083/api${path}`, { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    ...(body ? { method: 'POST', body: JSON.stringify(body) } : {}), signal: AbortSignal.timeout(10000) });
  assert.ok(response.ok, `API returned ${response.status}`);
  return response.json();
}
async function issue(learner, reviewer) {
  const entry = await api('/submissions', learner, { achievementId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', evidence: 'Synthetic identity recovery acceptance' });
  await api(`/submissions/${entry.submission.id}/approve`, reviewer, { expectedVersion: entry.version });
  return { submission: entry.submission.id, credential: await api(`/submissions/${entry.submission.id}/credential`, learner, {}) };
}

test('recovered identity retains changed accounts and authorizes new badge issuance', { timeout: 240000 }, async () => {
  const source = container('identity-db');
  const target = container('identity-restored-db');
  const identity = container('identity');
  assert.notEqual(source, target);
  const databases = JSON.parse(docker(['inspect', source, target]));
  const volumes = databases.map(info => info.Mounts.find(mount => mount.Destination === '/var/lib/postgresql/data')?.Name);
  for (const volume of volumes) assert.ok(volume?.startsWith(`${project}_`), 'Database must use a fixture volume');
  assert.notEqual(volumes[0], volumes[1], 'Source and target must not share storage');
  assert.equal(docker(['exec', target, 'psql', '-U', 'keycloak', '-d', 'keycloak', '-At', '-c', "SELECT count(*) FROM information_schema.tables WHERE table_schema='public'"]).toString().trim(), '0', 'Restore target must be empty');
  const [before] = JSON.parse(docker(['inspect', identity]));
  assert.ok(before.Config.Env.includes('KC_DB_URL_HOST=identity-db'));
  const learner = await login('learner');
  const reviewer = await login('reviewer');
  const owner = claims(learner);
  assert.match(owner.email, /^personal-[a-f0-9-]+@example\.test$/, 'Run account-continuity.test.mjs first');
  assert.equal(owner.email_verified, true);
  const original = await issue(learner, reviewer);
  const jwks = await (await fetch(`${issuer}/protocol/openid-connect/certs`)).json();
  docker([...compose, 'stop', 'identity']);
  const archive = docker(['exec', source, 'pg_dump', '-U', 'keycloak', '-d', 'keycloak', '--format=custom']);
  assert.ok(archive.length > 1000);
  const restore = ['exec', '-i', target, 'pg_restore', '-U', 'keycloak', '-d', 'keycloak', '--no-owner', '--no-acl', '--single-transaction'];
  docker(restore, Buffer.alloc(0), false);
  docker(restore, Buffer.from('corrupt backup'), false);
  docker(restore, archive);
  docker(restore, archive, false);
  archive.fill(0);
  docker([...compose, '-f', 'compose.identity-restored.yml', 'up', '-d', '--no-deps', '--force-recreate', 'identity']);
  let ready = false;
  for (let i = 0; i < 90; i++) {
    try { if ((await fetch(`${issuer}/.well-known/openid-configuration`, { signal: AbortSignal.timeout(1000) })).ok) { ready = true; break; } } catch {}
    await setTimeout(1000);
  }
  assert.ok(ready, 'Recovered identity did not become ready');
  const [after] = JSON.parse(docker(['inspect', container('identity')]));
  assert.equal(after.Image, before.Image);
  assert.ok(after.Config.Env.includes('KC_DB_URL_HOST=identity-restored-db'));
  assert.deepEqual(after.Config.Cmd, ['start-dev']);
  assert.deepEqual(await (await fetch(`${issuer}/protocol/openid-connect/certs`)).json(), jwks);
  const recovered = await login('learner');
  const recoveredReviewer = await login('reviewer');
  for (const key of ['sub', 'iss', 'aud', 'email', 'email_verified', 'realm_access', 'resource_access']) {
    assert.deepEqual(claims(recovered)[key], owner[key], `Recovered learner ${key}`);
    assert.deepEqual(claims(recoveredReviewer)[key], claims(reviewer)[key], `Recovered reviewer ${key}`);
  }
  assert.deepEqual(await api(`/submissions/${original.submission}/credential`, recovered), original.credential);
  const fresh = await issue(recovered, recoveredReviewer);
  assert.notEqual(fresh.credential.id, original.credential.id);
  for (const { credential } of [original, fresh]) {
    assert.equal((await api(`/credentials/${credential.id.split('/').pop()}/verify`, recovered, credential)).status, 'VALID');
  }
  console.log('Fresh authentication, changed verified email, stable ownership, retained issuer keys and new issuance survived identity database recovery');
});
