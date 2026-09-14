# Achievement catalog

Reviewers manage achievements in **Explore**. Choose **Create achievement** to enter a name and assessment criteria. Saving creates a draft visible only to reviewers. Review its saved criteria, then choose **Publish** to make it available to learners. Choose **Edit** to revise an entry before learners submit evidence. Publishing makes the saved version public; it does not issue a credential.

Names contain 1–120 characters and criteria 1–5,000 characters after trimming whitespace. Each entry has a UUID, a `published` flag and an optimistic `version`, starting at zero. Both editing and publication increment the version. Stale commands and repeated publication return 409 instead of silently overwriting another reviewer's work. A draft cannot accept submissions or appear as a pathway requirement or prerequisite.

The first evidence submission freezes the achievement's name and criteria. Later changes require a new achievement with a new ID. This preserves the assessment basis for pending reviews and the achievement embedded in issued credentials. Existing signed credentials remain unchanged. The service uses shared row locks for submissions and an exclusive row lock for edits so a submission and edit cannot bypass this rule through a race.

## API

| Method | Path | Behavior |
|---|---|---|
| GET | `/api/achievements` | Published entries only, ordered by name and ID |
| GET | `/api/achievements/{id}` | Published entry; drafts and unknown IDs both return 404 |
| GET | `/api/reviewer/achievements` | Reviewer; all entries, including drafts, with `Cache-Control: no-store` |
| GET | `/api/reviewer/achievements/{id}` | Reviewer; entry detail and the creation response's Location |
| POST | `/api/achievements` | Reviewer; create a draft with `name` and `criteria`, returns 201 |
| POST | `/api/achievements/{id}` | Reviewer; update with `name`, `criteria` and `expectedVersion` |
| POST | `/api/achievements/{id}/publish` | Reviewer; publish the saved draft with `expectedVersion` |

Invalid content returns 400, missing credentials 401, insufficient permission 403, an unknown update ID 404, and a stale or frozen update 409. The browser keeps entered content after a failed save. Catalog writes require the same reviewer role used by the review queue.

There is no destructive catalog deletion or unpublish endpoint. Achievement IDs referenced by submissions and credentials remain resolvable within the deployment's catalog. Create a new achievement ID when a frozen criterion needs a successor.

The database migration preserves previously stored achievements as published, without rewriting submissions or signed credentials. New API creations default to drafts; API clients must explicitly call the publication endpoint before using a new achievement for submissions or pathways. Restarting the service retains saved drafts and publication state.

API tests cover draft confidentiality, publication authorization, input validation, stale updates, freezing, concurrent publication and a submission/edit transaction race. Browser tests cover draft controls, publication and conflict feedback. The real-token smoke flow saves, edits and publishes a draft before issuing a credential against the authored criteria.
