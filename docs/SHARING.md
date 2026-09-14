# Sharing credentials

Credentials are private by default. Issuing a credential, verifying it or downloading JSON/PNG/SVG does not publish it. In **Public sharing**, review the signed document, select **I want to make this credential public**, then choose **Publish share link**. Copy the displayed link or open the shared badge page. Only the learner who owns a credential can change its sharing settings; reviewer privileges do not grant that permission.

The link lets anyone read and copy the original signed credential, including its achievement, issuer, dates, proof and salted recipient identifier. It does not expose private evidence, review history, the account's plain email address or other credentials. The public page works without signing in and provides a verification action and JSON download. Verification checks the issuer's registry, signature, validity period and revocation state at the time of the check; a public link alone does not establish validity.

Choose **Stop sharing** to deny subsequent public requests. Requests that already read the document may finish, and copies retained by recipients cannot be erased remotely. Public responses, including absent and disabled shares, use `Cache-Control: no-store`. Readers can select **Refresh shared credential** to fetch current availability; previously displayed copies cannot be recalled.

Sharing an expired or revoked award is allowed, but verification reports its actual status. The same controls apply to individual and pathway awards. Settings persist in PostgreSQL and update atomically with an ownership condition. Repeated requests are idempotent; concurrent updates are serialized by the database and the last applied update determines the stored setting.

## API

| Method | Route | Behavior |
| --- | --- | --- |
| GET | `/api/credentials/{id}/sharing` | Owner-only status: `enabled` and public page `url` (null while private) |
| POST | `/api/credentials/{id}/sharing` | Owner-only `{ "enabled": true/false }`; returns the applied status |
| GET | `/api/shared/credentials/{id}` | Original signed JSON when shared; identical empty 404 for absent and private credentials |
| GET | `/shared/{id}` | Public web page; no identity-provider initialization |

Private credential and image download endpoints retain their original ownership rules. Sharing does not change a signed credential or its revocation status. A public page URL uses the configured `CAMPUS_PUBLIC_URL`; it must be reachable by the people receiving it. Localhost links are suitable only for local demonstrations.

## Background

The private-by-default and explicit publication flow is informed by [Parchment's Open Badges 3.0 sharing guidance](https://community.instructure.com/en/kb/articles/663713-unknown). Signet Campus supplies its own sharing policy and does not copy the provider's service, branding or account-management behavior.
