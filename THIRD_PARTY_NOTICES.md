# Third-party notices

This document covers repository source, the Gradle wrapper, server dependencies, browser runtime dependencies and bundled fonts.

## Browser bundle

[Browser notices](web/public/THIRD_PARTY_NOTICES.txt) preserve the complete upstream license texts for React, React DOM, Scheduler, Zustand, TanStack Query, Keycloak JS and Inter. React, Zustand and TanStack components use MIT terms; Keycloak JS uses Apache-2.0; Inter uses SIL Open Font License 1.1. The generated browser bundle includes this notice file. Run `npm run build` in `web` to regenerate it from the locked runtime dependencies. `web/package-lock.json` also records development tools, package license declarations and integrity hashes. Development-only dependencies are not included in the browser bundle.

The decorative artwork was generated for Signet Campus. The original application's MIT license does not replace font or dependency licenses. Docker base images retain their own bundled-component terms; their layer contents are not included in the browser notice inventory.

## License boundary

The root MIT License covers original Signet Campus work. It does not replace the licenses of dependencies, the Gradle wrapper, standard documents, or the license/notice/POM evidence preserved here. Upstream copyright and notice texts are retained under their original terms.

The repository includes the unmodified Gradle wrapper scripts and JAR, originally from Gradle 8.14.3. See [the upstream distribution license](LICENSES/Gradle-LICENSE.txt), including its bundled-component notices. Source: [Gradle v8.14.3](https://github.com/gradle/gradle/tree/v8.14.3). Other runtime/test JARs are downloaded by Gradle; they are not committed or distributed as an application binary in this repository.

## Evidence for resolved software

- [171 resolved artifacts and SHA-256 hashes](docs/third-party/inventory.json).
- [Per-coordinate POM license declarations, including inherited parents](docs/DEPENDENCY_LICENSES.md).
- [Original POMs and license/notice files extracted from the resolved JARs](docs/third-party/).
- Reproduce extraction from `server` with `sh ./gradlew -I third-party.init.gradle thirdPartyInventory`. The script does not resolve license expressions or declare compatibility automatically.

| Component group | Reviewed licensing / treatment |
|---|---|
| Kotlin, Spring, Jackson, Kotest, Konsist, kotlin-logging, Tink, Nimbus, Titanium, rdf-urdna | Apache-2.0 declarations in artifact/POM evidence; retain license and applicable notices when redistributing. |
| RDF4J | EDL-1.0 (BSD-style), preserve copyright, conditions and disclaimer. |
| Parsson 1.1.7 | EPL-2.0 with conditional GPL-2.0 + Classpath Exception secondary licensing; use the EPL-2.0 path for this project's redistribution planning. |
| Jakarta JSON/Annotation and JUnit | EPL-related declarations; review exact component evidence and retain source/license notices when distributing covered programs. |
| Logback | Alternative EPL-2.0 / LGPL-2.1 terms; select and comply with EPL-2.0 for distribution planning. |
| JNA (test dependency) | Alternative LGPL / Apache-2.0 terms; Apache-2.0 route documented by its POM. Not currently included in an application distribution. |
| MIT/BSD dependencies | Preserve original copyright and license text. See per-artifact evidence. |

POM lists sometimes express alternatives without machine-readable AND/OR semantics. The table does not mechanically treat every listed license as cumulative. Actual artifact licenses take priority over a guessed SPDX expression.

## Signet libraries

The pinned [core `v0.1.3`](https://github.com/brody-0125/signet-core/tree/v0.1.3) and [starter `v0.1.4`](https://github.com/brody-0125/signet-spring-boot-starter/tree/v0.1.4) use Apache-2.0. Their published POMs declare the license, and their JARs include `META-INF/LICENSE` and `META-INF/NOTICE`. These files are retained in the per-artifact evidence directories and must accompany redistributed covered artifacts.

Parsson retains its own EPL-2.0 terms and conditional secondary-license designation; Signet's Apache-2.0 license does not replace them. Parsson's original license and NOTICE from the resolved JAR are preserved at [its artifact evidence directory](docs/third-party/org.eclipse.parsson/parsson/1.1.7/).

## Distribution obligations

Browser integration tests use unmodified Playwright and playwright-core 1.63.0 (Apache-2.0), installed as development dependencies. Original license, NOTICE and bundled third-party notices are preserved under `LICENSES/playwright/` and `LICENSES/playwright-core/`. They are not part of the application's browser bundle. Browser executables downloaded by Playwright retain their own licenses; they are CI/local tooling and are not distributed by this repository. Preserve their upstream notices if separately redistributing browser binaries.

The mail integration adds Spring Mail (Apache-2.0), Angus Activation and Jakarta Activation (EDL-1.0), and Angus Jakarta Mail 2.0.5. Jakarta Mail's bundled NOTICE specifies EPL-2.0 with a conditional GPL-2.0/ClassPath secondary license; this project retains the EPL-2.0 route and all embedded license/notice texts. Unmodified corresponding source is available from [Maven Central](https://repo.maven.apache.org/maven2/org/eclipse/angus/jakarta.mail/2.0.5/jakarta.mail-2.0.5-sources.jar). Keep this source-availability statement and the artifact's notices with any binary redistribution.

The local Compose service uses unmodified [Mailpit v1.31.1](https://github.com/axllent/mailpit/tree/v1.31.1), licensed under MIT. Its [license](LICENSES/Mailpit-LICENSE.txt) is retained here; container layers and bundled third-party components retain their respective terms. Mailpit is a separate local development service, not part of the browser bundle or application JAR.

- Apache-2.0: accompany distributed covered work with the license, preserve applicable copyright/attribution and NOTICE content, and identify modified upstream files. Trademark rights are not granted. See [Apache-2.0 sections 4 and 6](https://www.apache.org/licenses/LICENSE-2.0).
- EPL-2.0: preserve the license and source availability requirements for the covered Program when distributing it. Provide the exact corresponding source location and required notices; modifications to covered code must be handled under the applicable terms. Merely using a dependency does not make all original application code EPL. See [EPL-2.0](https://www.eclipse.org/legal/epl-2.0/).
- MIT/BSD/EDL: retain the component's license, attribution and disclaimer; root MIT alone does not satisfy another component's notices.
- For Parsson binary redistribution, the exact source is available at [tag 1.1.7](https://github.com/eclipse-ee4j/parsson/tree/1.1.7) and [Maven sources](https://repo.maven.apache.org/maven2/org/eclipse/parsson/parsson/1.1.7/parsson-1.1.7-sources.jar). No Parsson modifications are made here.

Build plugins and the JDK/container base are not exhaustively represented by the runtime/test inventory. Include dependency inventories, assets/fonts, toolchain and base-image notices for any additional distributed artifacts. Re-run review whenever dependency versions or distribution form change.
