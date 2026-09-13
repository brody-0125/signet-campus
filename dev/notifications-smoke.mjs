import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { setTimeout } from 'node:timers/promises';

const api = process.env.CAMPUS_API ?? 'http://localhost:8080';
const issuer = process.env.CAMPUS_ISSUER ?? 'http://localhost:8081/realms/signet-campus';
const inbox = process.env.MAILPIT_URL ?? 'http://localhost:8025';
async function login(username) {
  const response = await fetch(`${issuer}/protocol/openid-connect/token`, { method: 'POST',
    body: new URLSearchParams({ grant_type: 'password', client_id: 'campus-dev', username, password: `local-${username}-only` }) });
  assert.equal(response.status, 200);
  return (await response.json()).access_token;
}
async function request(path, token, body, expected = 200) {
  const response = await fetch(`${api}${path}`, { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    ...(body === undefined ? {} : { method: 'POST', body: JSON.stringify(body) }) });
  assert.equal(response.status, expected);
  return response.json();
}
const reviewer = await login('reviewer');
const learner = await login('learner');
const achievements = await request('/api/achievements', learner);
const name = `Notification check ${randomUUID()}`;
const path = await request('/api/pathways', reviewer, { name, description: 'Synthetic local notification check', achievementIds: [achievements[0].id] }, 201);
const enrolled = await request(`/api/pathways/${path.id}/enrollment`, learner, {});
assert.deepEqual(await request(`/api/pathways/${path.id}/enrollment`, learner, {}), enrolled);
let matching;
for (let attempt = 0; attempt < 30; attempt++) {
  const listing = await fetch(`${inbox}/api/v1/messages`);
  assert.equal(listing.status, 200);
  const messages = (await listing.json()).messages;
  matching = messages.filter(message => message.Snippet.includes(name));
  if (matching.length) break;
  await setTimeout(1000);
}
assert.equal(matching?.length, 1, 'One enrollment email should reach local Mailpit');
const response = await fetch(`${inbox}/api/v1/message/${matching[0].ID}`);
assert.equal(response.status, 200);
const message = await response.json();
assert.deepEqual(message.To.map(recipient => recipient.Address), ['learner@example.test']);
assert.match(message.MessageID, /^[0-9a-f-]{36}@signet-campus\.invalid$/);
assert.ok(message.Text.includes(name));
assert.ok(message.Text.includes(process.env.CAMPUS_PUBLIC_URL ?? 'http://localhost:5173'));
console.log(`Enrollment email verified: ${message.ID}; pathway ${path.id}; message ${message.MessageID}`);
