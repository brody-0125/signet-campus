# Independent credential verification

Campus acceptance tests check its actual issued credentials using a separate JavaScript implementation of JSON-LD, RDF canonicalization and Ed25519 Data Integrity verification. They do not rely on Signet's Java verifier to establish proof validity.

## Run

Start the local Docker services and wait for the API and identity provider. From the repository root:

```sh
npm ci --prefix dev --ignore-scripts
node dev/prepare-verification-resources.mjs
node --test dev/independent-verification.test.mjs
```

This creates synthetic achievements, submissions, pathway enrollments and credentials with the local learner/reviewer accounts, downloads their images, and revokes the resulting credentials. It preserves records for review. Use a disposable deployment when repeatable empty state is required. `CAMPUS_API` and `CAMPUS_ISSUER` can select another explicitly trusted test deployment; the defaults are the local API and Keycloak.

## Coverage

| Check | Evidence |
|---|---|
| Open Badges JSON schema | Ajv validates achievement and pathway completion credentials against the pinned official VC2-compatible schema; missing achievement data fails |
| Proof integrity | Digital Bazaar's `eddsa-rdfc-2022` suite verifies the actual signature using the published controller and Multikey |
| Controller authorization | The verification method must be authorized for assertion by the published controller; a wrong key or controller fails |
| Tampering | Changed achievement, recipient hash, proof bytes or purpose fails |
| Unicode | ASCII and decomposed Latin/Korean names pass; changing a decomposed signed name to NFC fails |
| Portable images | Existing smoke checks extract JSON from PNG iTXt and SVG metadata, compare it with the original document, then independently verify it |
| Lifecycle | Public revocation-list entries change the independent lifecycle result while the original signature remains intact; injected clock boundaries check future and expired results |
| Context resolution | Only downloaded, hash-checked resources and the explicitly fetched fixture controller/keys are available; unknown contexts fail without network fallback |

The test uses `@digitalbazaar/data-integrity`, `@digitalbazaar/eddsa-rdfc-2022-cryptosuite`, `jsonld-signatures`, Ajv and `ajv-formats`, pinned in `dev/package-lock.json`. These are test-only dependencies. Their full license texts are preserved in [acceptance tool notices](../dev/THIRD_PARTY_NOTICES.txt); regenerate with `node dev/verification-notices.mjs`.

## Contexts and compatibility

New credentials include the official Open Badges [extension context](https://purl.imsglobal.org/spec/ob/v3p0/extensions.json), which defines schema-validator, revocation-list and refresh-service types. The public issuer document uses the [Controlled Identifiers context](https://www.w3.org/TR/cid-1.0/#json-ld-context) and Multikey context so its verification methods and assertion relationships can be expanded by external verifiers.

Core 0.1.4 added the extension context. Core 0.1.5 preserves original Unicode code points rather than silently applying NFC during signing. Earlier credentials lacking extension definitions can fail strict JSON-LD validation; earlier decomposed-text credentials can have signatures over different text than their JSON contains. An application upgrade does not repair those signed documents. Review and explicitly reissue affected credentials; never append a context, normalize text or replace a signature on an existing credential and present it as the unchanged original. The public controller correction does not change badge signing keys or credential documents.

`dev/verification-resources.json` records official resource URLs and SHA-256 digests. Setup downloads their original bytes into ignored `secrets/verification-resources/`; verification checks the hashes again. A changed upstream resource fails preparation and requires review before updating a digest. Specification materials retain their upstream terms; see [standards attribution and distribution requirements](COMPLIANCE.md). The repository stores the resource manifest, not copies of these specification documents.

## Scope

This is an independent implementation check for Campus's credential formats and local trust configuration, not 1EdTech certification or a general-purpose verifier. It does not establish the truth of an achievement, a real person's identity, issuer accreditation, or support for every optional Open Badges API/proof suite. It does not upload credentials to third-party services. HTTPS deployment and public issuer discovery must also be validated for the intended service origin.

Technical references: [Open Badges 3.0](https://www.imsglobal.org/spec/ob/v3p0), [Data Integrity EdDSA](https://www.w3.org/TR/vc-di-eddsa/), [RDF Dataset Canonicalization](https://www.w3.org/TR/rdf-canon/), and the [independent cryptosuite implementation](https://github.com/digitalbazaar/eddsa-rdfc-2022-cryptosuite).
