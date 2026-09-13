# Work log

## 2026-09-14 — iteration 1: reproducible dependency publication

### Plan and acceptance

LIB-01: recover Maven publication before implementing the service. Prove a Kotlin consumer can resolve the remote starter and its transitive core, auto-configure signing, and reject tampering/wrong keys. Preserve library main branches.

### Red → green

- Starter: Docker/Java 17 `publishToMavenLocal` failed because the task did not exist. Added Maven publication, JitPack Java 17, LF wrapper attributes, correct consumption docs, and CI publication check.
- Starter: `build publishToMavenLocal` passes, 79 tests, zero failures/errors/skips. Remote CI also passes.
- Core: baseline had 118 tests / 12 failures after Windows checkout changed W3C N-Quads fixtures to CRLF. Added LF Git attributes for wrappers and N-Quads. Kept byte-exact assertions. `build publishToMavenLocal` passes all 118 tests, examples build too. Remote CI passes.
- Complexity review: native Gradle publishing and Git attributes suffice; no custom publishing scripts or test normalization utilities needed.

### Commits

- Starter: `abaf5c173ff6800f898e74dc73b4f01493559e00`, `fix/jitpack-publication`, pushed. [Branch](https://github.com/brody-0125/signet-spring-boot-starter/tree/fix/jitpack-publication). JitPack `abaf5c173f` reports ok and remote POM downloaded successfully.
- Core: `64155ebb7b07457616d7e70a3f80475a96d3a49d`, `fix/cross-platform-conformance`, pushed. [Branch](https://github.com/brody-0125/signet-core/tree/fix/cross-platform-conformance). JitPack `64155eb` reports ok; remote POM downloaded.
- Integration repo: `af89b94` establishes scope and acceptance matrix on `feature/campus-foundation`.
- No library merge or library main push performed. Integration repository was local at the end of iteration 1; see publication update below.

### Next iteration

1. Completed remote Kotlin dependency contract test: 1 test, zero failures/errors/skips. Real Data Integrity signing, correct/wrong public keys, tampered credential and Jackson linkage checked against remote starter `abaf5c173f`.
2. Verify latest core JitPack POM/JAR, update starter core version on a separate fix branch if consumer compatibility passes. Current starter still uses `8a98820f9c`; do not imply it includes latest core fixes.
3. Define issuance/verification contracts (approved evidence only, idempotency, stable keys, expiry/revocation/trust) and write failing Kotest domain tests.
4. Implement Kotlin hexagonal slice and PostgreSQL persistence, Konsist rules and Kover verification. Kover/Konsist are declared but gates are not yet implemented.
5. Add Docker OIDC/Postgres/key provisioning and real issuance/verification E2E; then frontend concepts and React workflow.

### Continuation locations

- Integration: this directory, `outputs/signet-campus`.
- Library clones: workspace `work/signet-core` and `work/signet-spring-boot-starter`.
- Docker JDK available: `eclipse-temurin:17-jdk`; no host Java on PATH.
- Docker Gradle caches: `signet-gradle-cache`, `signet-core-gradle-cache`.
- No secrets or real learner information used.

### Local consumer check

With Java 17 installed: `cd server` then `./gradlew test` (Windows: `gradlew.bat test`).
Or mount `server` to `/workspace` in `eclipse-temurin:17-jdk` and run `sh ./gradlew test --no-daemon` there.
This is a dependency integration test, not an implemented web service. Do not mark ISSUE-01, VERIFY-01 or OPS-01 complete from it.

## 2026-09-14 — repository publication

- Name: Signet Campus; repository: `brody-0125/signet-campus`.
- Set application version to 1.0.0 at the owner's request; this is a development baseline, not completed production functionality.
- Added MIT for original work, Gradle wrapper attribution, third-party notices, exact artifact evidence and license declarations, standards/privacy/deployment review, contribution rules and security reporting.
- Inventory verified: 142 unique coordinates, 81 runtime and 61 test-only; preserved scopes and SHA-256 hashes. Inherited POM licenses resolved; the two Signet artifacts have no POM license declarations and are explicitly flagged.
- Containerized Kotlin contract test still passes after version update. No secret/key patterns found in the source publication review.
- Initial integration `main` and `v1.0.0` establish this new repository's baseline; existing library main branches remain unchanged. Future implementation continues on feature/fix/refactor branches.
