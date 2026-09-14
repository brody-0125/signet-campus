# Application distributions

Build from the repository root so that both images can include the application license and notices:

```bash
docker compose build web server
# Equivalent individual builds:
docker build -f web/Dockerfile .
docker build -f server/Dockerfile .
```

The root Docker context excludes local environment files, secrets, Git data and build caches. Only the application sources and the selected licensing documents are copied into build stages.

## Browser artifact

`web/dist/THIRD_PARTY_NOTICES.txt` contains the original application MIT license, standards attribution and full licenses from locked browser runtime dependencies and fonts. It is available as `/THIRD_PARTY_NOTICES.txt` on the web server. Run `npm run build --prefix web` followed by `node --test dev/distribution-notices.test.mjs` to build and check the artifact.

## Server artifact

The executable Spring Boot JAR contains:

- `META-INF/LICENSE`, `META-INF/NOTICE` and `META-INF/THIRD_PARTY_NOTICES.md`.
- `META-INF/docs/third-party/`: coordinate-specific original POMs, extracted upstream licenses/notices and the dependency inventory.
- `META-INF/docs/DEPENDENCY_LICENSES.md`, this distribution guide and standards requirements.
- `META-INF/docs/covered-source/`: the exact unmodified MPL-covered Public Suffix List source bundled by OkHttp, its provenance and hash.
- `META-INF/LICENSES/MPL-2.0.txt`: the complete applicable license.

Nested dependency JARs retain their upstream contents. The inventory distinguishes runtime and test configurations; retaining test evidence does not add test libraries to the application classpath. The Docker runtime includes the same executable JAR at `/app/app.jar`.

From `server`, run `sh ./gradlew verifyDistributionNotices`. The same check runs during `check` and the server Docker build. It checks original application notices and verifies that the included Public Suffix List source reproduces the compiled rules in the packaged runtime dependency.

## Source availability

The following unmodified runtime components are redistributed under the EPL-2.0 route. Their exact corresponding source archives are publicly available from Maven Central; retain these links and their original bundled notices when redistributing the JAR or image.

| Component | Corresponding source |
|---|---|
| Logback Classic 1.5.32 | [Source archive](https://repo.maven.apache.org/maven2/ch/qos/logback/logback-classic/1.5.32/logback-classic-1.5.32-sources.jar) |
| Logback Core 1.5.32 | [Source archive](https://repo.maven.apache.org/maven2/ch/qos/logback/logback-core/1.5.32/logback-core-1.5.32-sources.jar) |
| Jakarta Annotation API 2.1.1 | [Source archive](https://repo.maven.apache.org/maven2/jakarta/annotation/jakarta.annotation-api/2.1.1/jakarta.annotation-api-2.1.1-sources.jar) |
| Jakarta JSON API 2.1.3 | [Source archive](https://repo.maven.apache.org/maven2/jakarta/json/jakarta.json-api/2.1.3/jakarta.json-api-2.1.3-sources.jar) |
| Angus Jakarta Mail 2.0.5 | [Source archive](https://repo.maven.apache.org/maven2/org/eclipse/angus/jakarta.mail/2.0.5/jakarta.mail-2.0.5-sources.jar) |
| Parsson 1.1.7 | [Source archive](https://repo.maven.apache.org/maven2/org/eclipse/parsson/parsson/1.1.7/parsson-1.1.7-sources.jar) |

OkHttp's MPL-covered list source is included directly in the distribution; see [its provenance](covered-source/README.md). Alternative or conditional license declarations do not replace the selected license route. Other dependency licenses and notices are preserved in the coordinate-specific evidence directories.

Container base layers and separately downloaded PostgreSQL, Keycloak, Mailpit, Prometheus and Zipkin images retain their own component licenses. The server inventory is not an inventory of these images. Preserve and review their upstream notices when distributing image layers or additional tools. Regenerate inventories and review source availability when changing dependency versions or the distribution format.
