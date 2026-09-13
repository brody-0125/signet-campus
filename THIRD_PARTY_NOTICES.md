# Third-party notices

Reviewed: 2026-09-14. Project version: 1.0.0. Scope: repository source, Gradle 8.14.3 wrapper, resolved server runtime and test dependencies. This is an evidence-based inventory, not a claim that every future deployment is legally cleared.

## License boundary

The root MIT License covers original Signet Campus work. It does not replace the licenses of dependencies, the Gradle wrapper, standard documents, or the license/notice/POM evidence preserved here. Upstream copyright and notice texts are retained under their original terms.

The repository includes the unmodified Gradle wrapper scripts and JAR, originally from Gradle 8.14.3. See [the upstream distribution license](LICENSES/Gradle-LICENSE.txt), including its bundled-component notices. Source: [Gradle v8.14.3](https://github.com/gradle/gradle/tree/v8.14.3). Other runtime/test JARs are downloaded by Gradle; they are not committed or distributed as an application binary in this repository.

## Evidence for resolved software

- [142 resolved artifacts and SHA-256 hashes](docs/third-party/inventory.json).
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

## Signet upstream metadata gaps

The pinned [core `8a98820f9c`](https://github.com/brody-0125/signet-core/tree/8a98820f9c) and [starter `v0.1.0`](https://github.com/brody-0125/signet-spring-boot-starter/tree/v0.1.0) identify Apache-2.0 in their READMEs, but their published POMs have no license declaration. Their linked LICENSE files are absent from the repositories. The inventory therefore intentionally marks the POM declaration UNKNOWN; it must not silently convert missing metadata to MIT.

Both upstream NOTICE files contain a misleading explanation that EPL secondary licensing allows Parsson to be distributed under Apache-2.0. That is not the secondary license designated in [Parsson 1.1.7's license](https://github.com/eclipse-ee4j/parsson/blob/1.1.7/LICENSE.md). Do not rely on that explanation. Parsson's original license and NOTICE from the resolved JAR are preserved at [its artifact evidence directory](docs/third-party/org.eclipse.parsson/parsson/1.1.7/).

Before publishing a bundled application or image: repair Signet license files/POM metadata/upstream explanation on separate fix branches, consume a verified corrected commit, and regenerate the inventory. The current source-only publication preserves this finding rather than asserting complete binary distribution clearance.

## Distribution obligations

- Apache-2.0: accompany distributed covered work with the license, preserve applicable copyright/attribution and NOTICE content, and identify modified upstream files. Trademark rights are not granted. See [Apache-2.0 sections 4 and 6](https://www.apache.org/licenses/LICENSE-2.0).
- EPL-2.0: preserve the license and source availability requirements for the covered Program when distributing it. Provide the exact corresponding source location and required notices; modifications to covered code must be handled under the applicable terms. Merely using a dependency does not make all original application code EPL. See [EPL-2.0](https://www.eclipse.org/legal/epl-2.0/).
- MIT/BSD/EDL: retain the component's license, attribution and disclaimer; root MIT alone does not satisfy another component's notices.
- For Parsson binary redistribution, the exact source is available at [tag 1.1.7](https://github.com/eclipse-ee4j/parsson/tree/1.1.7) and [Maven sources](https://repo.maven.apache.org/maven2/org/eclipse/parsson/parsson/1.1.7/parsson-1.1.7-sources.jar). No Parsson modifications are made here.

Build plugins and the JDK/container base are not exhaustively represented by the runtime/test inventory. Include dependency inventories, assets/fonts, toolchain and base-image notices for any additional distributed artifacts. Re-run review whenever dependency versions or distribution form change.
