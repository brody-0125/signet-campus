# Standards and distribution requirements

This document describes standards attribution, third-party rights and distribution requirements.

## Open Badges and W3C

Open Badges Specification v3.0: Copyright (c) 2025 1EdTech Consortium, Inc. All Rights Reserved. [Specification](https://www.imsglobal.org/spec/ob/v3p0), [Specification Document License](https://www.1edtech.org/standards/specification-license).

The 1EdTech license permits implementing products subject to its terms and requires visible attribution. It does not generally grant rights to create derivatives of the specification document. Keep attribution in the README and NOTICE; link to the specification rather than republishing its text. Only 1EdTech grants its compliance designations. Do not use certification logos or claim certified conformance without completing the applicable process. Naming the implemented standard does not claim endorsement.

W3C Verifiable Credentials Data Model v2.0 and proof specifications are referenced as technical sources. When copying schemas, JSON-LD contexts, test vectors or examples, inspect the license attached to that exact material and preserve its attribution and modification notices. Do not assume MIT applies. [W3C Software and Document License](https://www.w3.org/copyright/software-license-2023/) describes conditions for materials offered under that license. No W3C specification text is vendored in this repository.

## Trademarks

Third-party marks identify their projects or standards. Software licenses do not grant general trademark rights. No institutional logos, credential designs, course content or learner records are included.

## Personal information and credential publication

Current tests use synthetic addresses under reserved example domains. Before using real learner data, determine the operator, applicable jurisdictions, lawful processing basis, privacy notice, retention/deletion policy, processor agreements and transfer requirements. Relevant official starting points include [Korea's Personal Information Protection Commission](https://www.pipc.go.kr/eng/) and, where applicable, the [EU GDPR](https://eur-lex.europa.eu/eli/reg/2016/679/oj). Applicability depends on the actual operation, not the programming language or MIT license.

Public credential sharing requires appropriate privacy controls, including private evidence access restrictions, data minimization and retention policies. Hashing email with a salt does not guarantee anonymization.

## Before a binary or container release

1. Resolve the Signet upstream licensing metadata issues listed in [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md).
2. Generate the exact distribution inventory, including frontend assets/fonts, build tools where redistributed and container base layers. Preserve all required license/NOTICE files in the artifact and make covered source available as required.
3. Review vulnerability advisories, dependency locks, signing-key provisioning and third-party service terms. An inventory hash is provenance evidence, not a vulnerability scan.
4. Review encryption export/import rules for the actual distribution destinations when applicable.
