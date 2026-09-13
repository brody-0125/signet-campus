# Achievement catalog

Reviewers manage achievements in **Explore**. Choose **Create achievement** to enter a name and assessment criteria; choose **Edit** on an existing entry to revise it before learners submit evidence. New entries become visible to learners immediately.

Names contain 1–120 characters and criteria 1–5,000 characters after trimming whitespace. Each entry has a UUID and an optimistic `version`, starting at zero. Concurrent edits using a stale version return 409 instead of silently overwriting another reviewer's work.

The first evidence submission freezes the achievement's name and criteria. Later changes require a new achievement with a new ID. This preserves the assessment basis for pending reviews and the achievement embedded in issued credentials. Existing signed credentials remain unchanged. The service uses shared row locks for submissions and an exclusive row lock for edits so a submission and edit cannot bypass this rule through a race.

## API

| Method | Path | Behavior |
|---|---|---|
| GET | `/api/achievements` | Public catalog, ordered by name and ID; includes version |
| GET | `/api/achievements/{id}` | Public catalog entry; also used as the creation response's Location |
| POST | `/api/achievements` | Reviewer; create with `name` and `criteria`, returns 201 |
| POST | `/api/achievements/{id}` | Reviewer; update with `name`, `criteria` and `expectedVersion` |

Invalid content returns 400, missing credentials 401, insufficient permission 403, an unknown update ID 404, and a stale or frozen update 409. The browser keeps entered content after a failed save. Catalog writes require the same reviewer role used by the review queue.

There is no destructive catalog deletion endpoint. Achievement IDs referenced by submissions and credentials must remain resolvable within the deployment's catalog. The catalog currently publishes entries immediately; draft publication, archival and successor relationships are not implemented.

API tests cover authorization, input validation, stale updates, freezing and a submission/edit transaction race. Browser component tests and the real-token smoke flow cover authenticated creation and issuance against the authored criteria.
