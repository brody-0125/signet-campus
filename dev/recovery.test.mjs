import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { setTimeout } from 'node:timers/promises';

// Local rehearsal only: keep sensitive archive/SQL output in memory, never in test logs.
function command(args, input) {
  return spawnSync('docker', args, { input, maxBuffer: 64 * 1024 * 1024, timeout: 60000 });
}
function docker(args, input) {
  const result = command(args, input);
  assert.ok(result.status === 0, `Docker ${args[0]} failed (exit ${result.status}); output withheld because it may contain private records`);
  return result.stdout;
}
const inspect = name => JSON.parse(docker(['inspect', name]))[0];
const sql = (container, database, query) => docker(['exec', container, 'psql', '-U', 'campus', '-d', database, '-At', '-v', 'ON_ERROR_STOP=1', '-c', query]).toString().trim();
async function waitFor(check) {
  for (let attempt = 0; attempt < 60; attempt++) {
    if (check()) return;
    await setTimeout(1000);
  }
  assert.fail('Recovery service did not become ready within 60 attempts');
}

test('restore credentials and copied signing keys into an isolated service with mail disabled', { timeout: 240000 }, async () => {
  const sourceDb = docker(['compose', 'ps', '-q', 'db']).toString().trim();
  const sourceServer = docker(['compose', 'ps', '-q', 'server']).toString().trim();
  assert.ok(sourceDb && sourceServer, 'Start the local Compose stack and run dev/smoke.mjs first');
  const server = inspect(sourceServer);
  const db = inspect(sourceDb);
  const prefix = `campus-recovery-${randomUUID()}`;
  const target = `${prefix}-db`;
  const keys = `${prefix}-keys`;
  const api = `${prefix}-api`;
  const created = [];
  let networkCreated = false;
  let keysCreated = false;
  const records = JSON.parse(sql(sourceDb, 'campus', "SELECT coalesce(json_agg(c ORDER BY id), '[]') FROM credentials c"));
  assert.ok(records.length > 0 && records.some(record => record.revoked_at), 'Run dev/smoke.mjs to provide signed and revoked credentials');
  const tables = sql(sourceDb, 'campus', "SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename").split('\n');
  const fingerprint = (container, database, table) => {
    assert.match(table, /^[a-z_]+$/);
    return sql(container, database, `SELECT count(*), md5(coalesce(string_agg(row_to_json(t)::text, E'\\n' ORDER BY row_to_json(t)::text), '')) FROM ${table} t`);
  };
  const before = tables.map(table => fingerprint(sourceDb, 'campus', table));
  try {
    docker(['network', 'create', '--internal', prefix]); networkCreated = true;
    docker(['volume', 'create', keys]); keysCreated = true;
    docker(['run', '-d', '--name', target, '--network', prefix, '--tmpfs', '/var/lib/postgresql/data',
      '-e', 'POSTGRES_USER=campus', '-e', 'POSTGRES_PASSWORD=local-recovery-only', '-e', 'POSTGRES_DB=campus_recovery', db.Image]);
    created.push(target);
    await waitFor(() => command(['exec', target, 'pg_isready', '-U', 'campus', '-d', 'campus_recovery']).status === 0);

    const restore = ['exec', '-i', target, 'pg_restore', '-U', 'campus', '-d', 'campus_recovery', '--no-owner', '--no-acl', '--single-transaction'];
    assert.notEqual(command(restore, Buffer.alloc(0)).status, 0, 'Empty input must fail');
    assert.notEqual(command(restore, Buffer.from('not a PostgreSQL archive')).status, 0, 'Corrupt input must fail');
    assert.equal(sql(target, 'campus_recovery', "SELECT count(*) FROM pg_tables WHERE schemaname = 'public'"), '0');
    const archive = docker(['exec', sourceDb, 'pg_dump', '-U', 'campus', '-d', 'campus', '--format=custom']);
    assert.ok(archive.subarray(0, 5).toString() === 'PGDMP', 'Expected a custom-format archive');
    docker(restore, archive);
    assert.notEqual(command(restore, archive).status, 0, 'A second restore into populated tables must fail');
    assert.deepEqual(tables.map(table => fingerprint(target, 'campus_recovery', table)), before, 'All public table rows must survive restore; run with no concurrent writes');

    // Copy keys inside Docker, without printing/exporting private key material to the host.
    docker(['run', '--rm', '--network', 'none', '--volumes-from', `${sourceServer}:ro`, '--mount', `type=volume,src=${keys},dst=/backup`,
      '--entrypoint', 'sh', db.Image, '-c', 'cp -a /run/secrets/. /backup/']);
    const environment = server.Config.Env.filter(value => !/^(DATABASE_|NOTIFICATIONS_ENABLED=|SMTP_)/.test(value));
    docker(['run', '-d', '--name', api, '--network', prefix, '--mount', `type=volume,src=${keys},dst=/run/secrets,readonly`,
      ...environment.flatMap(value => ['-e', value]), '-e', `DATABASE_URL=jdbc:postgresql://${target}:5432/campus_recovery`,
      '-e', 'DATABASE_USER=campus', '-e', 'DATABASE_PASSWORD=local-recovery-only', '-e', 'NOTIFICATIONS_ENABLED=false', server.Image]);
    created.push(api);
    const publicCheck = `
      const response = await fetch('http://${api}:8080/actuator/health/readiness', {signal: AbortSignal.timeout(1500)});
      if (!response.ok) process.exit(1);
    `;
    await waitFor(() => command(['run', '--rm', '--network', prefix, 'node:24-alpine', 'node', '--input-type=module', '-e', publicCheck]).status === 0);
    const verify = `
      import assert from 'node:assert/strict';
      let input = ''; for await (const chunk of process.stdin) input += chunk;
      const records = JSON.parse(input);
      for (const record of records) {
        const response = await fetch('http://${api}:8080/api/credentials/' + record.id + '/verify', {
          method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(record.document), signal: AbortSignal.timeout(10000)
        });
        assert.equal(response.status, 200);
        const status = (await response.json()).status;
        const expected = record.revoked_at ? 'REVOKED' : new Date(record.issued_at) > new Date() ? 'NOT_YET_VALID' : new Date(record.valid_until) <= new Date() ? 'EXPIRED' : 'VALID';
        assert.equal(status, expected);
        const shared = await fetch('http://${api}:8080/api/shared/credentials/' + record.id);
        assert.equal(shared.status, record.shared ? 200 : 404);
        assert.equal(shared.headers.get('cache-control'), 'no-store');
      }
    `;
    docker(['run', '--rm', '-i', '--network', prefix, 'node:24-alpine', 'node', '--input-type=module', '-e', verify], JSON.stringify(records));
    assert.ok(inspect(api).Config.Env.includes('NOTIFICATIONS_ENABLED=false'));
    console.log(`Recovery verified: ${tables.length} tables, ${records.length} credential records, signatures and sharing; no host ports or SMTP access`);
  } finally {
    const cleanup = created.reverse().map(name => command(['rm', '-f', name]).status);
    if (keysCreated) cleanup.push(command(['volume', 'rm', keys]).status);
    if (networkCreated) cleanup.push(command(['network', 'rm', prefix]).status);
    assert.ok(cleanup.every(status => status === 0), `Could not remove every temporary resource with prefix ${prefix}`);
  }
});
