# Credential issuance and verification

Learners issue an Open Badges credential from an approved submission using their account's verified email. The credential embeds the achievement and issuer profile and carries an Ed25519 `eddsa-rdfc-2022` Data Integrity proof produced by the published Signet Spring Boot starter.

One credential is stored per submission. Repeated and concurrent requests return the same stored document. PostgreSQL enforces uniqueness and checks approved status and ownership when inserting. Credentials expire after 365 days. Private evidence text is not copied into the credential.

The recipient identifier is a salted email hash. Each credential uses a separate public salt to reduce correlation. The salt is not secret: hashing does not guarantee anonymity or prevent guessing known addresses. Download and sharing decisions must account for this identifier.

## API

| Method | Path | Access / behavior |
|---|---|---|
| POST | `/api/submissions/{id}/credential` | Owner with verified email; issue or return existing credential |
| GET | `/api/submissions/{id}/credential` | Owner; retrieve issued credential |
| GET | `/api/credentials/{id}` | Owner; download credential JSON |
| POST | `/api/credentials/{id}/verify` | Public; submit complete credential JSON for registry verification |
| POST | `/api/credentials/{id}/revoke` | Reviewer; revoke idempotently |
| GET | `/api/issuers/bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb` | Public; controller document and Multikey verification method |

Credential responses use `application/vc+ld+json` and `Cache-Control: no-store`. Verification accepts up to 131,072 characters and returns `status` and `valid`. Only `VALID` has `valid: true`.

| Status | Meaning |
|---|---|
| VALID | Original document, valid signature, current validity period and no revocation |
| UNKNOWN_CREDENTIAL | ID absent from this issuer's registry |
| ALTERED | Different or malformed JSON |
| INVALID_PROOF | Invalid signature, issuer, verification method, proof purpose or suite |
| NOT_YET_VALID | Current time precedes issuance |
| EXPIRED | Current time is at or after expiry |
| REVOKED | Reviewer revoked the credential |

JSON member order is ignored, but extra properties are rejected even if JSON-LD processing would discard them. The verifier processes only the stored document after comparing the supplied document. It never retrieves caller-supplied URLs, contexts or keys.

This endpoint verifies this Campus deployment's issued credentials. It is not a general verifier for other organizations or a claim of 1EdTech certification. Revocation uses the local registry; a portable standards-based status list for external verifiers is not yet published.

## Keys and deployment

Compose generates a private Ed25519 JWK once in the `signing-keys` volume and preserves existing keys. The server mounts it read-only as UID 10001. Startup validates the key type and private/public component match. The public controller endpoint exposes only a public Multikey.

Outside Compose, provide `SIGNING_KEY_PATH` and set `CAMPUS_PUBLIC_URL` to a stable HTTPS web origin. Local HTTP accepts only `localhost` or `127.0.0.1`. Credential and issuer identifiers use that origin's `/api` path.

Back up the database and signing key together using restricted storage. Do not delete the volume or replace the key to rotate it: historical verification currently requires the original key and public origin. Multiple replicas must receive the same securely provisioned key. The cryptography port supports integration with a managed signer; multi-key rotation and a managed-key adapter are not implemented yet.

## Browser and tests

In **My submissions**, open an approved submission and select **Issue badge**, then **Download JSON** or **Verify badge**. Reissuing retrieves the original document.

`docker compose --profile test run --rm tests` covers authorization, approval gating, concurrent issuance, document integrity, expiry boundaries and revocation. `node dev/smoke.mjs` exercises real JWTs. Restart the server and pass the printed submission ID to the smoke script to verify persistence.

The data model and validity semantics follow the [Open Badges specification](https://www.imsglobal.org/spec/ob/v3p0). Campus supplements library signature verification with issuer, proof-purpose, registry and lifecycle checks.
