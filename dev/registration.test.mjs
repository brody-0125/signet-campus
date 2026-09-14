import { test } from 'node:test';
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { setTimeout } from 'node:timers/promises';
import { chromium } from '../web/node_modules/playwright/index.mjs';

const issuer = 'http://localhost:8084/realms/signet-campus';
const tokenRequest = (username, password) => fetch(`${issuer}/protocol/openid-connect/token`, {
  method: 'POST', body: new URLSearchParams({ grant_type: 'password', client_id: 'campus-dev', username, password }),
});
const api = (path, token, body) => fetch(`http://localhost:8083/api${path}`, {
  headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
  ...(body === undefined ? {} : { method: 'POST', body: JSON.stringify(body) }),
});
async function success(response, status = 200) {
  assert.equal(response.status, status);
  return response.json();
}

test('a new learner registers, verifies email and earns a badge without reviewer privileges', { timeout: 180000 }, async () => {
  for (const url of [`${issuer}/.well-known/openid-configuration`, 'http://localhost:8083/api/achievements']) {
    let ready = false;
    for (let i = 0; i < 60; i++) {
      try { if ((await fetch(url, { signal: AbortSignal.timeout(1500) })).ok) { ready = true; break; } } catch {}
      await setTimeout(1000);
    }
    assert.ok(ready, 'Start the isolated account fixture before this test');
  }
  const username = `new-${randomUUID()}`;
  const email = `${username}@example.test`;
  const password = `local-${randomUUID()}-only`;
  const browser = await chromium.launch({ ...(process.env.CAMPUS_BROWSER_CHANNEL ? { channel: process.env.CAMPUS_BROWSER_CHANNEL } : {}) });
  try {
    const page = await browser.newPage();
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto('http://localhost:5183/');
    await page.getByRole('button', { name: 'Sign in', exact: true }).click();
    await page.getByRole('link', { name: 'Register', exact: true }).waitFor({ timeout: 5000 });
    await page.getByRole('link', { name: 'Register', exact: true }).click();
    await page.locator('#username').fill(username);
    await page.locator('#email').fill(email);
    await page.locator('#firstName').fill('New');
    await page.locator('#lastName').fill('Learner');
    await page.locator('#password').fill(password);
    await page.locator('#password-confirm').fill(password);
    await page.getByRole('button', { name: 'Register', exact: true }).click();
    let message;
    for (let i = 0; i < 30; i++) {
      const listing = await (await fetch('http://localhost:8026/api/v1/messages')).json();
      message = listing.messages.find(item => item.To.some(recipient => recipient.Address === email));
      if (message) break;
      await setTimeout(1000);
    }
    assert.ok(message, 'Registration must send a verification message to local Mailpit');
    assert.equal(page.url().startsWith(issuer), true, 'Unverified registration must remain with the identity provider');
    const blocked = await tokenRequest(username, password);
    assert.equal(blocked.status, 400);
    assert.equal((await blocked.json()).error, 'invalid_grant');
    const mail = await (await fetch(`http://localhost:8026/api/v1/message/${message.ID}`)).json();
    assert.deepEqual(mail.To.map(recipient => recipient.Address), [email]);
    const link = mail.Text.match(/http:\/\/localhost:8084\/[^\s<>]+/)?.[0];
    assert.ok(link, 'Expected a local email confirmation link');
    await page.goto(link);
    await page.getByRole('button', { name: 'Account', exact: true }).waitFor();
    assert.equal(await page.getByRole('button', { name: 'Review queue', exact: true }).count(), 0);
    const token = (await success(await tokenRequest(username, password))).access_token;
    const claims = JSON.parse(Buffer.from(token.split('.')[1], 'base64url'));
    assert.match(claims.sub, /^[0-9a-f-]{36}$/);
    assert.equal(claims.email, email);
    assert.equal(claims.email_verified, true);
    assert.ok(!claims.realm_access?.roles?.includes('reviewer'));
    assert.equal((await api('/reviewer/achievements', token)).status, 403);
    assert.deepEqual(await success(await api('/submissions', token)), []);
    const reviewer = (await success(await tokenRequest('reviewer', 'local-reviewer-only'))).access_token;
    const achievementId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
    const pathway = await success(await api('/pathways', reviewer, { name: `New learner ${username}`, description: 'Synthetic registration acceptance', achievementIds: [achievementId] }), 201);
    await success(await api(`/pathways/${pathway.id}/enrollment`, token, {}));
    const submission = await success(await api('/submissions', token, { achievementId, evidence: 'Synthetic new learner keyboard audit' }), 201);
    const id = submission.submission.id;
    assert.equal(submission.submission.learnerId, claims.sub);
    assert.equal((await api(`/submissions/${id}/approve`, token, { expectedVersion: 0 })).status, 403);
    await success(await api(`/submissions/${id}/approve`, reviewer, { expectedVersion: 0 }));
    const credential = await success(await api(`/submissions/${id}/credential`, token, {}));
    const status = await success(await api(`/credentials/${credential.id.split('/').pop()}/verify`, token, credential));
    assert.equal(status.status, 'VALID');
    const fresh = (await success(await tokenRequest(username, password))).access_token;
    assert.equal(JSON.parse(Buffer.from(fresh.split('.')[1], 'base64url')).sub, claims.sub);
    assert.deepEqual(await success(await api(`/submissions/${id}/credential`, fresh)), credential);
    const other = (await success(await tokenRequest('learner', 'local-learner-only'))).access_token;
    assert.equal((await api(`/submissions/${id}/credential`, other)).status, 404);
    await page.getByRole('button', { name: 'Account', exact: true }).click();
    await page.getByRole('button', { name: 'Manage account', exact: true }).click();
    await page.getByRole('heading', { name: 'Personal info', exact: true }).waitFor();
    assert.equal(await page.getByRole('textbox', { name: /^Email/ }).inputValue(), email);
    assert.deepEqual(errors, [], 'Registration must not produce browser runtime errors');
    console.log('New learner registration, verification, enrollment, issuance, private ownership and account access verified');
  } finally { await browser.close(); }
});
