# Learning pathways

Pathways group achievements into a learning objective. Reviewers publish a name, description and 1–50 different required achievements in **Pathways → Create pathway**. They may also select up to 50 prerequisite achievements for enrollment. Learners can inspect both sets of criteria, earn prerequisite badges, enroll and work toward the completion requirements.

Every required achievement must have at least one current credential issued to the enrolled learner. Pending, rejected or approved-but-unissued submissions do not count. Multiple credentials for the same achievement count once. Credentials earned before enrollment count while they remain current. The server calculates progress from its issuance registry on every request; revoked, expired and future-dated records are excluded. **Refresh progress** updates the browser after issuance or revocation elsewhere.

Progress describes current holdings and can return to incomplete after expiry or revocation. It is separate from a signed completion award. This view does not independently re-verify signatures or accept imported credentials. Use the credential verification endpoint to check a document's proof.

Published pathway membership is immutable. Create a new pathway when its requirements or prerequisites change. Achievement criteria follow the [catalog policy](CATALOG.md), including freezing on first submission. Completion requirements may be pursued in any order. [Enrollment emails](NOTIFICATIONS.md) are queued for verified account addresses and captured by local Mailpit. Ordered steps within a pathway and withdrawal are not implemented yet.

## Completion awards

An enrolled learner with every required current badge can select **Issue badge** under **Your pathway award**. A verified account email is required. The server issues a signed Open Badges credential with a separate pathway source; it does not create an evidence submission or imply an additional reviewer approval. The embedded achievement identifies the public pathway and lists its immutable required achievement IDs.

The award records completion at the issuance decision and is valid for 365 days unless a reviewer explicitly revokes it. Later expiry or revocation of component badges changes live progress but does not automatically cancel the completion award. An incorrect award must be revoked separately. Existing awards remain downloadable even when live progress becomes incomplete. Repeated requests return the original document, including after its revocation or expiry; automatic renewal is not supported.

Eligibility is checked again in the database INSERT using one statement snapshot and timestamp. A component revocation committed after that snapshot does not retroactively invalidate the decision. A unique constraint on pathway and learner prevents duplicate completion credentials across concurrent requests and server instances. Completion credentials share the existing signing, private download, public verification and revocation-list lifecycle. A credential has exactly one source: an approved submission or a pathway enrollment.

## Enrollment prerequisites

Prerequisites are an entry condition, separate from completion requirements. The two sets cannot overlap. First enrollment requires at least one current, non-revoked credential for each prerequisite, belonging to the authenticated learner. Approval without issuance is insufficient. Expired, future-dated and revoked credentials do not qualify. Imported credentials are not considered.

The enrollment INSERT evaluates eligibility using one database statement snapshot and timestamp. If any prerequisite is missing, the API returns 409 and creates neither an enrollment nor an email event. A revocation committed after that snapshot does not retroactively undo enrollment. Existing registrations remain valid when prerequisites later expire or are revoked; repeat enrollment requests return the same registration. Completion is still calculated only from current completion badges.

Existing pathways and requests that omit `prerequisiteAchievementIds` have no prerequisites. Prerequisite changes require a new pathway ID. This policy defines entry requirements, not a global restriction on submitting evidence for individual achievements.

## API

| Method | Path | Access and result |
|---|---|---|
| GET | `/api/pathways` | Public catalog |
| GET | `/api/pathways/{id}` | Public pathway, including ordered `achievementIds` |
| POST | `/api/pathways` | Reviewer; `name`, `description`, `achievementIds`, optional `prerequisiteAchievementIds`; returns 201 and Location |
| POST | `/api/pathways/{id}/enrollment` | Signed-in learner; idempotent enrollment; returns progress |
| GET | `/api/pathways/{id}/progress` | Signed-in learner's own progress; 404 if not enrolled |
| POST | `/api/pathways/{id}/credential` | Enrolled learner with verified email and current completion badges; idempotent signed award; 409 if incomplete |
| GET | `/api/pathways/{id}/credential` | Learner's own existing completion award; 404 if absent |

Names and descriptions are trimmed and limited to 120 and 5,000 characters. Empty completion sets, duplicate/unknown IDs, excessive sets or overlap between prerequisites and completion requirements return 400 and the creation transaction rolls back. Missing authentication returns 401; authoring without reviewer permission returns 403. Unknown pathways return 404; unmet enrollment prerequisites return 409. Public pathway responses include both `achievementIds` and `prerequisiteAchievementIds`.

Progress returns `pathwayId`, `enrolledAt`, `requirements` (achievement ID and earned flag), `earned`, `total` and `completed`. Enrollment and progress responses use `Cache-Control: no-store`. The learner identity comes from the bearer token; callers cannot select another learner. Public catalog responses contain no enrollment or learner data.

Public pathway records also contain `paused`. An archived requirement or prerequisite pauses new enrollment (423), while existing enrollment and progress remain available. Already-enrolled learners with all required current badges can still claim the completion award. Other unfinished work waits for achievement restoration. Creating a new pathway with an archived achievement also returns 423. See [archive and restore](CATALOG.md#archive-and-restore).

A database primary key on pathway and learner makes enrollment idempotent across server instances. Requirement foreign keys prevent dangling achievements. Progress uses one SQL statement and one statement timestamp for a consistent view of credential validity.

## Background

The pathway concept is informed by [Bowdoin's digital badge pathway guidance](https://bowdoin.teamdynamix.com/TDClient/1814/Portal/KB/Article/157578/Digital-Badge-Learning-Pathway-Subscription-Email-Notifications). The all-required and current-credential completion policies above belong to Signet Campus; they do not describe Bowdoin's internal rules.
