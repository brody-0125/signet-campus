# Contributing

Contributions to original project code are submitted under the repository MIT License. Only contribute material you have the right to submit. Preserve third-party licenses and identify copied or modified upstream files. No additional CLA is required.

Use `feature/<topic>` for new features, `fix/<topic>` for defects, and `refactor/<topic>` for improvements. Branch names must describe the work without model/vendor names. Use Conventional Commits (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`).

For each change: plan → acceptance criteria/test matrix → failing test → implementation → relevant verification → correctness and ponytail complexity review → justified simplification → commit → next plan. Open a pull request for review; do not merge library changes directly into main.

Run `cd server` then `sh ./gradlew test`. Add domain, architecture and integration checks as those features become real. Do not report unimplemented acceptance criteria as passed.

When changing dependencies, regenerate evidence using `sh ./gradlew -I third-party.init.gradle thirdPartyInventory`, review inherited POM licenses and embedded notices, and update THIRD_PARTY_NOTICES.md and docs/DEPENDENCY_LICENSES.md. The collector covers runtime/test artifacts, not the entire build toolchain or future containers/frontend. Review those separately before distributing them. Never commit credentials, private signing keys, learner records or private evidence.
