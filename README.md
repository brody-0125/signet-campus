# Signet Campus

Explore [learning pathways](docs/PATHWAYS.md), enroll and track progress toward a set of required achievements.

Local [enrollment notifications](docs/NOTIFICATIONS.md) are captured in Mailpit at `http://localhost:8025`.

Evidence submission and review for campus skills recognition, integrated with Open Badges credentials.

## Run locally

```bash
docker compose up -d --build web
```

Open `http://localhost:5173` to explore achievements, submit evidence and follow reviews. The API runs at `http://localhost:8080` with PostgreSQL storage and Keycloak authentication. See [Local development](docs/LOCAL_DEVELOPMENT.md) for accounts, configuration and smoke tests.

For an isolated deployment with HTTPS login, issuance and public revocation checks, follow [HTTPS deployment](docs/HTTPS.md). It serves the application at `https://localhost:8443` and keeps database, API and identity ports internal.

## Credential integration

Reviewers can [create and edit achievements](docs/CATALOG.md) from Explore. Criteria become immutable after the first evidence submission to preserve the basis of assessment and issuance.

The Kotlin server module integrates the published Signet Spring Boot starter. Its contract test builds a credential, signs it with an Ed25519 Data Integrity proof, verifies the signature, and checks rejection of modified credentials and unrelated public keys.

## Requirements

- Java 17
- Access to Maven Central and JitPack

Gradle is included through the wrapper.

## Run the tests

```bash
git clone https://github.com/brody-0125/signet-campus.git
cd signet-campus/server
sh ./gradlew test
```

On Windows, run `.\gradlew.bat test` from `server/`.

To run tests in Docker from the repository root:

```bash
docker run --rm -v "$PWD:/workspace" -w /workspace/server eclipse-temurin:17-jdk sh ./gradlew test --no-daemon
```

See [Testing](docs/TESTING.md) for covered behaviors, report locations and dependency inventory generation.

## Dependencies

| Component | Version |
|---|---|
| Kotlin | 2.2.21 |
| Spring Boot | 3.5.11 |
| Gradle | 8.14.3 |
| Signet Spring Boot starter | `v0.1.4` |
| Kotest | 6.0.4 |
| Konsist | 0.17.3 |
| Kover | 0.9.3 |
| kotlin-logging | 7.0.13 |

The [dependency inventory](docs/third-party/inventory.json) records resolved runtime and test artifacts, scopes and SHA-256 hashes. [License declarations](docs/DEPENDENCY_LICENSES.md) and [third-party notices](THIRD_PARTY_NOTICES.md) accompany the inventory.

## Documentation

- [Submission API](docs/API.md)
- [Credential issuance and verification](docs/CREDENTIALS.md)
- [Portable PNG and SVG badges](docs/PORTABLE_BADGES.md)
- [Private and public credential sharing](docs/SHARING.md)
- [Web interface and design system](docs/WEB_INTERFACE.md)
- [Account access and email changes](docs/ACCOUNT.md)
- [Local development](docs/LOCAL_DEVELOPMENT.md)
- [Backup and recovery](docs/RECOVERY.md)
- [Evidence review](docs/EVIDENCE_REVIEW.md)
- [Testing](docs/TESTING.md)
- [Contributing](CONTRIBUTING.md)
- [Security reporting](SECURITY.md)
- [Changelog](CHANGELOG.md)
- [Standards and distribution requirements](docs/COMPLIANCE.md)

## Standards

Credential integration uses [1EdTech Open Badges 3.0](https://www.imsglobal.org/spec/ob/v3p0) and [W3C Verifiable Credentials Data Model 2.0](https://www.w3.org/TR/vc-data-model-2.0/). Signet Campus is not certified by 1EdTech. See [NOTICE](NOTICE) for attribution.

## License

Original code and documentation are licensed under the [MIT License](LICENSE). Third-party components, the Gradle wrapper and preserved upstream notices retain their original licenses. See [Third-party notices](THIRD_PARTY_NOTICES.md).
