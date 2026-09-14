import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import Ajv from 'ajv/dist/2019.js';
import addFormats from 'ajv-formats';
import { DataIntegrityProof } from '@digitalbazaar/data-integrity';
import { cryptosuite } from '@digitalbazaar/eddsa-rdfc-2022-cryptosuite';
import jsigs from 'jsonld-signatures';

export async function createVerifier(api) {
  const resources = JSON.parse(await readFile(new URL('./verification-resources.json', import.meta.url)));
  const documents = new Map();
  for (const resource of resources) {
    const bytes = await readFile(new URL(`../secrets/verification-resources/${resource.file}`, import.meta.url));
    assert.equal(createHash('sha256').update(bytes).digest('hex'), resource.sha256, `Changed resource: ${resource.file}`);
    documents.set(resource.url, JSON.parse(bytes));
  }
  const ajv = new Ajv({ strict: false, allErrors: true });
  addFormats(ajv);
  const schema = ajv.compile(documents.get(resources.find(item => item.file === 'openbadges-schema.json').url));
  const issuerUrl = `${api}/api/issuers/bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb`;
  const response = await fetch(issuerUrl, { signal: AbortSignal.timeout(10000) });
  assert.equal(response.status, 200);
  const controller = await response.json();
  documents.set(controller.id, controller);
  for (const key of controller.verificationMethod) documents.set(key.id, { '@context': controller['@context'], ...key });
  // Only explicit fixture endpoints above/below use HTTP. JSON-LD/key lookup never fetches URLs.
  const documentLoader = async url => {
    assert.ok(documents.has(url), `Untrusted verification URL: ${url}`);
    return { documentUrl: url, document: structuredClone(documents.get(url)), contextUrl: null };
  };
  const verifyProof = document => jsigs.verify(document, {
    suite: new DataIntegrityProof({ cryptosuite }), purpose: new jsigs.purposes.AssertionProofPurpose(), documentLoader,
  });
  const lifecycle = (document, revoked, now = Date.now()) =>
    now < Date.parse(document.validFrom) ? 'NOT_YET_VALID' : now >= Date.parse(document.validUntil) ? 'EXPIRED' : revoked ? 'REVOKED' : 'VALID';

  return async (credential, expected, negativeCases = false) => {
    assert.ok(schema(credential), JSON.stringify(schema.errors));
    assert.equal(credential.issuer.id, controller.id);
    const proof = await verifyProof(credential);
    assert.ok(proof.verified, `Independent proof failed: ${JSON.stringify(proof.error)}`);
    assert.equal(credential.credentialStatus.id, `${controller.id.split('/issuers/')[0]}/revocations`);
    const statusResponse = await fetch(`${api}/api/revocations`, { signal: AbortSignal.timeout(10000) });
    assert.equal(statusResponse.status, 200);
    const status = await statusResponse.json();
    assert.equal(status.issuer, controller.id);
    assert.equal(lifecycle(credential, status.revokedCredentials.some(entry => entry.id === credential.id && entry.revoked === true)), expected);
    if (!negativeCases) return;
    if (credential.name !== credential.name.normalize('NFC')) {
      const normalized = structuredClone(credential);
      normalized.name = normalized.name.normalize('NFC');
      assert.equal((await verifyProof(normalized)).verified, false, 'Changing Unicode composition must fail');
    }
    for (const [name, mutate] of [
      ['achievement', value => { value.credentialSubject.achievement.name = 'Changed'; }],
      ['recipient', value => { value.credentialSubject.identifier[0].identityHash = 'sha256$' + '0'.repeat(64); }],
      ['signature', value => { value.proof.proofValue = value.proof.proofValue.slice(0, -1) + (value.proof.proofValue.endsWith('1') ? '2' : '1'); }],
      ['purpose', value => { value.proof.proofPurpose = 'authentication'; }],
      ['undefined property', value => { value.unmappedClaim = 'Not in the signed vocabulary'; }],
      ['unknown context', value => { value['@context'].push('https://untrusted.example/context'); }],
    ]) {
      const altered = structuredClone(credential);
      mutate(altered);
      assert.equal((await verifyProof(altered)).verified, false, `${name} must fail`);
    }
    const key = documents.get(credential.proof.verificationMethod);
    const saved = structuredClone(key);
    try {
      key.publicKeyMultibase = key.publicKeyMultibase.slice(0, -1) + (key.publicKeyMultibase.endsWith('1') ? '2' : '1');
      assert.equal((await verifyProof(credential)).verified, false, 'Wrong key must fail');
      Object.assign(key, saved);
      key.controller = 'https://untrusted.example/issuer';
      assert.equal((await verifyProof(credential)).verified, false, 'Wrong controller must fail');
    } finally { documents.set(key.id, saved); }
    const authorized = controller.assertionMethod;
    try {
      controller.assertionMethod = authorized.filter(id => id !== credential.proof.verificationMethod);
      assert.equal((await verifyProof(credential)).verified, false, 'A valid key without assertion authorization must fail');
    } finally { controller.assertionMethod = authorized; }
    const missing = structuredClone(credential);
    delete missing.credentialSubject.achievement;
    assert.equal(schema(missing), false, 'Schema must reject missing achievement');
    assert.equal(lifecycle(credential, false, Date.parse(credential.validFrom) - 1), 'NOT_YET_VALID');
    assert.equal(lifecycle(credential, false, Date.parse(credential.validUntil)), 'EXPIRED');
    console.log('Independent schema, EdDSA proof, tampering, key/controller, context and validity checks passed');
  };
}
