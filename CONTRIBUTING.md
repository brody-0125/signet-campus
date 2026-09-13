# Contributing

Contributions to original project code are submitted under the repository MIT License. Only contribute material you have the right to submit. Preserve third-party licenses and identify copied or modified upstream files. No additional CLA is required.

Use `feature/<topic>` for new features, `fix/<topic>` for defects, and `refactor/<topic>` for improvements. Use Conventional Commits (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`).

Include tests for behavior changes and regression tests for bug fixes. Keep changes focused and open a pull request for review.

Run `cd server` then `sh ./gradlew test`. See [Testing](docs/TESTING.md) for test coverage and reports.

Write documentation primarily in English. Describe the software's behavior, interfaces, configuration, usage and licensing. Keep investigation logs, conversation context, internal plans and environment-specific working notes out of repository documentation.

When changing dependencies, regenerate evidence using `sh ./gradlew -I third-party.init.gradle thirdPartyInventory`, review inherited POM licenses and embedded notices, and update THIRD_PARTY_NOTICES.md and docs/DEPENDENCY_LICENSES.md. The collector covers runtime/test artifacts, not the entire build toolchain or future containers/frontend. Review those separately before distributing them. Never commit credentials, private signing keys, learner records or private evidence.
