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
| GET | `/api/revocations` | Public; issuer's revocation list as JSON, with no-store caching |
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

This endpoint verifies this Campus deployment's issued credentials. It is not a general verifier for other organizations or a claim of 1EdTech certification.

## Public revocation list

New credentials include a signed `credentialStatus` reference with `type: 1EdTechRevocationList` and the URL of `/api/revocations`. External verifiers can GET that URL with `Accept: application/json` and look for the credential's full ID in `revokedCredentials`. Each entry contains only `id` and Boolean `revoked: true`; active credential IDs, learner details, review reasons and evidence are not published. The list is read from PostgreSQL for each request and carries `Cache-Control: no-store` so a new request reflects committed revocations.

The response contains `id`, `issuer` and `revokedCredentials`, following the protocol in the [1EdTech Revocation List Status Method](https://www.imsglobal.org/spec/vcrl/v1p0/). A missing ID means only that this issuer has not listed it as revoked. Verifiers must also validate the signature, issuer trust and validity period; list membership alone does not establish credential validity.

The protocol requires HTTPS with TLS 1.2 or 1.3. The default localhost HTTP setup demonstrates behavior but does not satisfy that transport requirement. The separate [HTTPS deployment](HTTPS.md) exercises both protocol versions with certificate validation. Production must terminate TLS at the public origin and preserve `/api/revocations` routing. Use the stable origin configured before issuance. The list currently contains all revoked IDs in one response; partitioned lists are not implemented.

Previously issued documents remain unchanged. Documents without a status reference can still be checked through the Campus registry endpoint, but external verifiers cannot discover this list from those documents alone. Revoked legacy IDs are included in the list using their original stored credential IDs.

## Keys and deployment

Compose generates a private Ed25519 JWK once in the `signing-keys` volume and preserves existing keys. The server mounts it read-only as UID 10001. Startup validates the key type and private/public component match. The public controller endpoint exposes only a public Multikey.

Outside Compose, provide `SIGNING_KEY_PATH` and set `CAMPUS_PUBLIC_URL` to a stable HTTPS web origin. Local HTTP accepts only `localhost` or `127.0.0.1`. Credential and issuer identifiers use that origin's `/api` path.

Back up the database, active private key and retained public key set using restricted storage. Historical verification requires the original public keys and stable issuer origin, not the old private keys. A managed-key adapter is not implemented yet.

### Signing key rotation

`SIGNING_KEY_PATH` selects the active Ed25519 private JWK. `VERIFICATION_KEYS` selects a Spring resource URI containing a public JWK Set (`{"keys":[...]}`). Its default is an empty bundled set. The server always trusts the active key's public component plus the configured public set. Unknown proof method IDs are rejected without remote retrieval. Private keys, malformed sets and other curves in the public set prevent startup.

The issuer controller publishes all trusted public methods and their `assertionMethod` references. Method IDs use public-key thumbprints, so retaining a public key preserves its method ID. Removing a retired key from the set removes local trust in credentials signed by it; retirement from issuance alone should not remove historical verification trust. A compromised active key must also be replaced. Coordinate incident response and credential revocation separately; this configuration does not undo signatures or already cached external trust decisions.

The preparation utility requires Node 24 and writes a new directory containing a new private JWK and a public set with previous, active and new public keys. It leaves active files unchanged and refuses an existing destination, including a partially written one. Files use mode 0600 and the directory 0700 on POSIX. Run it inside the Compose container for local Linux permissions; protect Windows files with appropriate ACLs if preparing on Windows.

For the initial Compose key, prepare a unique directory:

```sh
docker compose run --rm -v ./dev/prepare-key-rotation.mjs:/workspace/prepare-key-rotation.mjs:ro key-init node /workspace/prepare-key-rotation.mjs /keys/campus-signing.jwk /keys/rotation-01
```

For later rotations, pass the current signing file and the existing public set as the third argument. Preserve all historical public keys that must remain trusted. Keep private files out of source control.

Deploy in two stages using your deployment's environment or Compose `.env`:

1. Set `VERIFICATION_KEYS=file:/run/secrets/rotation-01/campus-verification.jwks`, keep the current `SIGNING_KEY_PATH`, and recreate all server replicas. Verify the controller publishes both methods and old credentials still verify. With Compose, use `docker compose up -d server`.
2. After every replica trusts the new public key, set `SIGNING_KEY_PATH=/run/secrets/rotation-01/campus-signing.jwk` and recreate servers. Verify newly issued credentials use the new method while old credentials still verify. Preserve the combined public set during rollback as well.

These files are loaded at startup. Provision identical public sets to every replica before activating the new signer. Existing signed documents and revocation records are not modified by rotation. Securely archive or destroy retired private material according to the operator's retention policy; the preparation tool does neither automatically.

## Browser and tests

In **My submissions**, open an approved submission and select **Issue badge**, then **Download JSON** or **Verify badge**. Reissuing retrieves the original document.

`docker compose --profile test run --rm tests` covers authorization, approval gating, concurrent issuance, document integrity, expiry boundaries and revocation. `node dev/smoke.mjs` exercises real JWTs. Restart the server and pass the printed submission ID to the smoke script to verify persistence.

The data model and validity semantics follow the [Open Badges specification](https://www.imsglobal.org/spec/ob/v3p0). Campus supplements library signature verification with issuer, proof-purpose, registry and lifecycle checks.
