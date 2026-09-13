import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { setTimeout } from 'node:timers/promises';
import http from 'node:http';
import https from 'node:https';

const docker = (...args) => execFileSync('docker', args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim();
const root = fileURLToPath(new URL('../', import.meta.url));
const echo = `require('http').createServer(async (req,res) => {
  let body=''; for await(const chunk of req) body+=chunk;
  res.setHeader('Content-Type','application/json');
  res.end(JSON.stringify({generation:process.env.GENERATION,url:req.url,method:req.method,body,host:req.headers.host,proto:req.headers['x-forwarded-proto']}));
}).listen(8080,'0.0.0.0');`;

test('running HTTP and HTTPS proxies follow changed Docker addresses and preserve requests', { timeout: 120000 }, async () => {
  const prefix = `campus-proxy-${randomUUID()}`;
  const volume = `${prefix}-certs`;
  const names = [];
  const run = (suffix, ...args) => {
    const name = `${prefix}-${suffix}`;
    names.push(name);
    docker('run', '-d', '--name', name, '--network', prefix, ...args);
    return name;
  };
  const inspect = name => JSON.parse(docker('inspect', name))[0];
  const request = (url, ca) => new Promise((resolve, reject) => {
    const req = (ca ? https : http).request(url, { method: 'POST', ca, timeout: 1500, headers: { 'Content-Type': 'application/json' } }, res => {
      let body = '';
      res.on('data', chunk => { body += chunk; });
      res.on('end', () => resolve({ status: res.statusCode, body }));
    });
    req.on('timeout', () => req.destroy(new Error('Request timeout')));
    req.on('error', reject);
    req.end('{"evidence":"keep this body"}');
  });
  const expectGeneration = async (url, ca, generation) => {
    let last;
    for (let attempt = 0; attempt < 20; attempt++) {
      try {
        last = await request(url, ca);
        if (last.status === 200 && JSON.parse(last.body).generation === generation) {
          const result = JSON.parse(last.body);
          assert.equal(result.url, new URL(url).pathname + new URL(url).search);
          assert.equal(result.method, 'POST');
          assert.equal(result.body, '{"evidence":"keep this body"}');
          assert.equal(result.host, ca ? new URL(url).host : new URL(url).hostname);
          if (ca) assert.equal(result.proto, 'https');
          return;
        }
      } catch (error) { last = error.message; }
      await setTimeout(500);
    }
    assert.fail(`Proxy did not reach ${generation} at ${url}: ${JSON.stringify(last)}`);
  };
  try {
    docker('network', 'create', prefix);
    const subnet = JSON.parse(docker('network', 'inspect', prefix))[0].IPAM.Config[0].Subnet;
    docker('network', 'rm', prefix);
    // Explicit IP reservation requires a user-configured subnet on Linux Docker engines.
    docker('network', 'create', '--subnet', subnet, prefix);
    docker('volume', 'create', volume);
    docker('run', '--rm', '--mount', `type=volume,src=${volume},dst=/certs`, '--mount', `type=bind,src=${root}dev/generate-tls-certificate.sh,dst=/generate.sh,readonly`,
      'eclipse-temurin:17-jdk', 'sh', '/generate.sh');
    const ca = docker('run', '--rm', '--mount', `type=volume,src=${volume},dst=/certs,readonly`, 'eclipse-temurin:17-jdk', 'cat', '/certs/server.crt');
    const backend = run('backend', '--network-alias', 'server', '--network-alias', 'identity', '-e', 'GENERATION=first', 'node:24-alpine', 'node', '-e', echo);
    const proxies = [false, true].map(tls => {
      const port = tls ? 8443 : 8080;
      const name = run(tls ? 'https' : 'http', '-p', `127.0.0.1::${port}`, '--mount',
        `type=bind,src=${root}web/nginx${tls ? '.https' : ''}.conf,dst=/etc/nginx/conf.d/default.conf,readonly`,
        '--mount', `type=volume,src=${volume},dst=/etc/nginx/tls,readonly`, 'nginxinc/nginx-unprivileged:stable-alpine');
      const details = inspect(name);
      return { name, started: details.State.StartedAt, origin: `${tls ? 'https' : 'http'}://127.0.0.1:${details.NetworkSettings.Ports[`${port}/tcp`][0].HostPort}`, ca: tls ? ca : undefined };
    });
    const routes = proxies.flatMap(proxy => (proxy.ca ? ['/api/check?filter=a%2Fb', '/auth/check?client_id=campus-dev'] : ['/api/check?filter=a%2Fb'])
      .map(path => ({ ...proxy, url: proxy.origin + path })));
    for (const route of routes) await expectGeneration(route.url, route.ca, 'first');
    const oldAddress = inspect(backend).NetworkSettings.Networks[prefix].IPAddress;
    docker('rm', '-f', backend);
    // Occupy the old address to guarantee replacement actually changes the DNS answer.
    run('old-address', '--ip', oldAddress, 'node:24-alpine', 'node', '-e', 'setInterval(() => {}, 1000)');
    const replacement = run('replacement', '--network-alias', 'server', '--network-alias', 'identity', '-e', 'GENERATION=second', 'node:24-alpine', 'node', '-e', echo);
    assert.notEqual(inspect(replacement).NetworkSettings.Networks[prefix].IPAddress, oldAddress);
    for (const route of routes) await expectGeneration(route.url, route.ca, 'second');
    for (const proxy of proxies) assert.equal(inspect(proxy.name).State.StartedAt, proxy.started);
  } finally {
    for (const name of names.reverse()) { try { docker('rm', '-f', name); } catch {} }
    try { docker('network', 'rm', prefix); } catch {}
    try { docker('volume', 'rm', volume); } catch {}
  }
});
