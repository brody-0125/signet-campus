# Standards, rights and deployment review

Reviewed 2026-09-14 for the 1.0.0 source baseline. This document records implementation obligations and unresolved distribution questions; it is not a certification or a jurisdiction-specific legal opinion.

## Open Badges and W3C

Open Badges Specification v3.0: Copyright (c) 2025 1EdTech Consortium, Inc. All Rights Reserved. [Specification](https://www.imsglobal.org/spec/ob/v3p0), [Specification Document License](https://www.1edtech.org/standards/specification-license).

The 1EdTech license permits implementing products subject to its terms and requires visible attribution. It does not generally grant rights to create derivatives of the specification document. Keep attribution in the README and NOTICE; link to the specification rather than republishing its text. Only 1EdTech grants its compliance designations. Do not use certification logos or claim certified conformance without completing the applicable process. Naming the implemented standard does not claim endorsement.

W3C Verifiable Credentials Data Model v2.0 and proof specifications are referenced as technical sources. When copying schemas, JSON-LD contexts, test vectors or examples, inspect the license attached to that exact material and preserve its attribution and modification notices. Do not assume MIT applies. [W3C Software and Document License](https://www.w3.org/copyright/software-license-2023/) describes conditions for materials offered under that license. No W3C specification text is vendored in this repository baseline.

## Trademarks and research material

Signet Campus is a descriptive project name; no trademark registration or availability opinion is claimed. Bowdoin is a research reference, not a customer or partner. No institutional logo, credential design, course content or learner record is included. Keep research summaries independently written with source links. Third-party marks are used only to identify their projects or standards; code licenses do not grant general trademark rights.

## Personal information and credential publication

Current tests use synthetic addresses under reserved example domains. Before using real learner data, determine the operator, applicable jurisdictions, lawful processing basis, privacy notice, retention/deletion policy, processor agreements and transfer requirements. Relevant official starting points include [Korea's Personal Information Protection Commission](https://www.pipc.go.kr/eng/) and, where applicable, the [EU GDPR](https://eur-lex.europa.eu/eli/reg/2016/679/oj). Applicability depends on the actual operation, not the programming language or MIT license.

Implementation acceptance criteria must cover explicit public-sharing choice, private evidence access control, data minimization, audited issuance/revocation and deletion/retention behavior. Hashing email with a salt is not a blanket guarantee of anonymization. Do not put personal evidence into an irrevocably public credential by default. Local notifications must remain in a test mail sink until real sending is authorized.

## Before a binary or container release

1. Resolve the Signet upstream licensing metadata issues listed in [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md).
2. Generate the exact distribution inventory, including frontend assets/fonts, build tools where redistributed and container base layers. Preserve all required license/NOTICE files in the artifact and make covered source available as required.
3. Review vulnerability advisories, dependency locks, signing-key provisioning and third-party service terms. An inventory hash is provenance evidence, not a vulnerability scan.
4. Run the acceptance matrix, including authorization, expiry/revocation, key persistence/rotation, SSRF controls and private/public data separation.
5. Review encryption export/import rules for the actual distribution destinations when applicable; no export classification has been determined for this development baseline.

The 1.0.0 tag identifies this source baseline; it does not certify completion of this checklist.
