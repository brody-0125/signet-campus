import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { setTimeout } from 'node:timers/promises';
import { chromium } from '../web/node_modules/playwright/index.mjs';

const issuer = 'http://localhost:8084/realms/signet-campus';
async function login(username) {
  const response = await fetch(`${issuer}/protocol/openid-connect/token`, { method: 'POST', body: new URLSearchParams({
    grant_type: 'password', client_id: 'campus-dev', username, password: `local-${username}-only`,
  }) });
  assert.equal(response.status, 200);
  return (await response.json()).access_token;
}
async function api(path, token, body) {
  const response = await fetch(`http://localhost:8083/api${path}`, { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    ...(body ? { method: 'POST', body: JSON.stringify(body) } : {}) });
  assert.ok(response.ok, `API ${path} returned ${response.status}`);
  return response.json();
}
test('verified personal email preserves subject, original credential and new issuance', { timeout: 240000 }, async () => {
  for (const url of [`${issuer}/.well-known/openid-configuration`, 'http://localhost:8083/actuator/health/readiness']) {
    let ready = false;
    for (let i = 0; i < 60; i++) {
      try { if ((await fetch(url, { signal: AbortSignal.timeout(1500) })).ok) { ready = true; break; } } catch {}
      await setTimeout(1000);
    }
    assert.ok(ready, `Service unavailable: ${url}`);
  }
  const learner = await login('learner');
  const reviewer = await login('reviewer');
  const submit = async token => {
    const entry = await api('/submissions', token, { achievementId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', evidence: 'Synthetic account continuity audit' });
    await api(`/submissions/${entry.submission.id}/approve`, reviewer, { expectedVersion: entry.version });
    return entry.submission.id;
  };
  const source = await submit(learner);
  const original = await api(`/submissions/${source}/credential`, learner, {});
  const browser = await chromium.launch({ ...(process.env.CAMPUS_BROWSER_CHANNEL ? { channel: process.env.CAMPUS_BROWSER_CHANNEL } : {}) });
  const page = await browser.newPage();
  const email = `personal-${randomUUID()}@example.test`;
  try {
    await page.goto('http://localhost:5183/');
    await page.getByRole('button', { name: 'Sign in', exact: true }).click();
    await page.getByRole('textbox', { name: /username|email/i }).fill('learner');
    await page.getByLabel('Password', { exact: true }).fill('local-learner-only');
    await page.getByRole('button', { name: 'Sign In', exact: true }).click();
    await page.getByRole('button', { name: 'Account', exact: true }).click();
    await page.getByRole('button', { name: 'Manage account', exact: true }).click();
    await page.getByRole('heading', { name: 'Personal info', exact: true }).waitFor();
    await page.getByRole('textbox', { name: /^Email/ }).fill(email);
    const saved = page.waitForResponse(response => response.request().method() === 'POST' && new URL(response.url()).pathname === '/realms/signet-campus/account/');
    await page.getByRole('button', { name: 'Save', exact: true }).click();
    assert.ok((await saved).ok());
    const unverified = await fetch(`${issuer}/protocol/openid-connect/token`, { method: 'POST', body: new URLSearchParams({
      grant_type: 'password', client_id: 'campus-dev', username: 'learner', password: 'local-learner-only',
    }) });
    assert.equal(unverified.status, 400, 'Fresh password-grant login must wait for email verification');
    assert.equal((await unverified.json()).error, 'invalid_grant');
    assert.deepEqual(await api(`/submissions/${source}/credential`, learner), original);
    await page.getByRole('link', { name: 'Back to campus-dev' }).click();
    await page.getByRole('button', { name: /^Sign (in|out)$/ }).waitFor();
    if (await page.getByRole('button', { name: 'Sign out', exact: true }).count()) await page.getByRole('button', { name: 'Sign out', exact: true }).click();
    await page.getByRole('button', { name: 'Sign in', exact: true }).click();
    await page.waitForURL(url => url.origin === 'http://localhost:8084');
    await page.getByRole('heading').first().waitFor();
    if (await page.getByRole('textbox', { name: /username|email/i }).count()) {
      await page.getByRole('textbox', { name: /username|email/i }).fill('learner');
      await page.getByLabel('Password', { exact: true }).fill('local-learner-only');
      await page.getByRole('button', { name: 'Sign In', exact: true }).click();
    }
    let message;
    for (let i = 0; i < 30; i++) {
      const listing = await (await fetch('http://localhost:8026/api/v1/messages')).json();
      message = listing.messages.find(item => item.To.some(recipient => recipient.Address === email));
      if (message) break;
      await setTimeout(1000);
    }
    assert.ok(message, 'A verification email must reach only the isolated Mailpit inbox');
    const mail = await (await fetch(`http://localhost:8026/api/v1/message/${message.ID}`)).json();
    assert.deepEqual(mail.To.map(recipient => recipient.Address), [email]);
    const link = mail.Text.match(/http:\/\/localhost:8084\/[^\s<>]+/)?.[0];
    assert.ok(link, 'Expected a local verification link');
    await page.goto(link);
    await page.getByRole('button', { name: 'Account', exact: true }).waitFor();
    const updated = await login('learner');
    const claims = JSON.parse(Buffer.from(updated.split('.')[1], 'base64url'));
    assert.equal(claims.sub, '11111111-1111-4111-8111-111111111111');
    assert.equal(claims.email, email);
    assert.equal(claims.email_verified, true);
    assert.deepEqual(await api(`/submissions/${source}/credential`, updated), original);
    const next = await api(`/submissions/${await submit(updated)}/credential`, updated, {});
    assert.notEqual(next.id, original.id);
    for (const credential of [original, next]) assert.equal((await api(`/credentials/${credential.id.split('/').pop()}/verify`, updated, credential)).status, 'VALID');
    console.log('Verified email, stable subject, unchanged original badge and fresh issuance confirmed');
  } finally { await browser.close(); }
});
