# Learning pathways

Pathways group achievements into a learning objective. Reviewers publish a name, description and 1–50 different required achievements in **Pathways → Create pathway**. Learners can inspect the requirements, enroll and open each achievement's criteria to submit evidence.

Every required achievement must have at least one current credential issued to the enrolled learner. Pending, rejected or approved-but-unissued submissions do not count. Multiple credentials for the same achievement count once. Credentials earned before enrollment count while they remain current. The server calculates progress from its issuance registry on every request; revoked, expired and future-dated records are excluded. **Refresh progress** updates the browser after issuance or revocation elsewhere.

Completion describes current holdings, not a permanent award. It can return to incomplete after expiry or revocation. This view does not independently re-verify signatures or accept imported credentials. Use the credential verification endpoint to check a document's proof.

Published pathway membership is immutable. Create a new pathway when the set of required achievements changes. Achievement criteria follow the [catalog policy](CATALOG.md), including freezing on first submission. Requirements may be pursued in any order. Ordered prerequisites, enrollment emails, withdrawal and a separate completion credential are not implemented yet.

## API

| Method | Path | Access and result |
|---|---|---|
| GET | `/api/pathways` | Public catalog |
| GET | `/api/pathways/{id}` | Public pathway, including ordered `achievementIds` |
| POST | `/api/pathways` | Reviewer; `name`, `description`, `achievementIds`; returns 201 and Location |
| POST | `/api/pathways/{id}/enrollment` | Signed-in learner; idempotent enrollment; returns progress |
| GET | `/api/pathways/{id}/progress` | Signed-in learner's own progress; 404 if not enrolled |

Names and descriptions are trimmed and limited to 120 and 5,000 characters. Empty, duplicate or unknown requirements return 400 and the creation transaction rolls back. Missing authentication returns 401; authoring without reviewer permission returns 403. Unknown pathways return 404.

Progress returns `pathwayId`, `enrolledAt`, `requirements` (achievement ID and earned flag), `earned`, `total` and `completed`. Enrollment and progress responses use `Cache-Control: no-store`. The learner identity comes from the bearer token; callers cannot select another learner. Public catalog responses contain no enrollment or learner data.

A database primary key on pathway and learner makes enrollment idempotent across server instances. Requirement foreign keys prevent dangling achievements. Progress uses one SQL statement and one statement timestamp for a consistent view of credential validity.

## Background

The pathway concept is informed by [Bowdoin's digital badge pathway guidance](https://bowdoin.teamdynamix.com/TDClient/1814/Portal/KB/Article/157578/Digital-Badge-Learning-Pathway-Subscription-Email-Notifications). The all-required and current-credential completion policies above belong to Signet Campus; they do not describe Bowdoin's internal rules.
