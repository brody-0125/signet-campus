# Testing

## Requirements

- Java 17
- Network access to Maven Central and JitPack

The Gradle 8.14.3 wrapper is included in `server/`.

## Run tests

From `server/`:

```bash
sh ./gradlew test
```

On Windows:

```powershell
.\gradlew.bat test
```

The HTML report is written to `server/build/reports/tests/test/index.html`.

## Credential contract

`SignetDependencyTest` uses the published Signet Spring Boot starter to check:

- Spring auto-configuration creates credential builder and signer beans.
- An Ed25519 Data Integrity credential verifies with the corresponding public key.
- Verification rejects an unrelated public key.
- Verification rejects a modified credential name.
- Jackson serializes and reads the credential structure.

Keys are generated in memory. Recipient addresses use synthetic example domains. These checks exercise the dependency contract; they do not cover issuer authorization, credential expiry or revocation policy.

## Dependency inventory

```bash
sh ./gradlew -I third-party.init.gradle thirdPartyInventory
```

The collector writes resolved runtime/test coordinates, artifact SHA-256 hashes, POMs and embedded license notices to `docs/third-party/`. Review POM parent licenses and update `docs/DEPENDENCY_LICENSES.md` when changing dependencies.
