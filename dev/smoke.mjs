import assert from 'node:assert/strict';

const api = process.env.CAMPUS_API ?? 'http://localhost:8080';
const issuer = process.env.CAMPUS_ISSUER ?? 'http://localhost:8081/realms/signet-campus';
async function login(username, password) {
  const response = await fetch(`${issuer}/protocol/openid-connect/token`, {
    method: 'POST',
    body: new URLSearchParams({ grant_type: 'password', client_id: 'campus-dev', username, password }),
  });
  assert.equal(response.status, 200, `Login failed for ${username}`);
  return (await response.json()).access_token;
}
async function request(path, token, body, expected = 200) {
  const response = await fetch(`${api}${path}`, {
    method: body === undefined ? 'GET' : 'POST',
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
  assert.equal(response.status, expected, `${path}: expected ${expected}, got ${response.status}`);
  return response.headers.get('content-type')?.includes('json') ? response.json() : null;
}

const learner = await login('learner', 'local-learner-only');
const reviewer = await login('reviewer', 'local-reviewer-only');
if (process.argv[2]) {
  const record = await request(`/api/submissions/${process.argv[2]}`, learner);
  assert.equal(record.submission.status, 'APPROVED');
  console.log(`Persisted approval verified: ${record.submission.id}`);
} else {
  await request('/api/achievements', null, undefined, 401);
  await request('/api/achievements', `${learner}corrupted`, undefined, 401);
  const [achievement] = await request('/api/achievements', learner);
  const record = await request('/api/submissions', learner, {
    achievementId: achievement.id, evidence: 'Synthetic keyboard navigation audit',
  }, 201);
  const path = `/api/submissions/${record.submission.id}`;
  await request(`${path}/approve`, learner, { expectedVersion: 0 }, 403);
  const approved = await request(`${path}/approve`, reviewer, { expectedVersion: 0 });
  assert.equal(approved.submission.status, 'APPROVED');
  await request(`${path}/approve`, reviewer, { expectedVersion: 0 }, 409);
  assert.equal((await request(path, learner)).version, 1);
  console.log(`Authenticated submission and review verified: ${record.submission.id}`);
}
