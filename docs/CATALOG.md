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
| POST | `/api/achievements/{id}/archive` | Reviewer; set `archived` to true or false with `expectedVersion` |

Invalid content returns 400, missing credentials 401, insufficient permission 403, an unknown update ID 404, and a stale or frozen update 409. The browser keeps entered content after a failed save. Catalog writes require the same reviewer role used by the review queue.

There is no destructive catalog deletion or unpublish endpoint. Achievement IDs referenced by submissions and credentials remain resolvable within the deployment's catalog. Create a new achievement ID when a frozen criterion needs a successor.

The database migration preserves previously stored achievements as published, without rewriting submissions or signed credentials. New API creations default to drafts; API clients must explicitly call the publication endpoint before using a new achievement for submissions or pathways. Restarting the service retains saved drafts and publication state.

API tests cover draft confidentiality, publication authorization, input validation, stale updates, freezing, concurrent publication and a submission/edit transaction race. Browser tests cover draft controls, publication and conflict feedback. The real-token smoke flow saves, edits and publishes a draft before issuing a credential against the authored criteria.

## Archive and restore

Choose **Archive** on a published achievement and review the effects before confirming. Archived achievements disappear from discovery, but their original public detail URLs remain available. `GET /api/achievements?includeArchived=true` includes published historical entries for evidence and pathway displays; it never includes drafts. Reviewer catalog entries show `archived`, and **Restore** resumes the same achievement ID. Drafts cannot be archived. Archived criteria cannot be edited.

Archiving pauses new submissions, resubmissions, first credential issuance and creation of new pathways referencing the achievement. Those requests return 423. Existing pending evidence can still be approved or rejected, but first issuance waits for restoration. No submission, review, enrollment, requirement or signed credential is rewritten or deleted. Previously issued credentials remain readable, exportable and shareable; archive is separate from revocation and does not change their validity checks.

Pathways with an archived requirement or prerequisite report `paused: true` and stop new enrollment. Existing learners retain their enrollment and progress. If they already hold every required current badge, they can still receive the independent pathway completion award. Unfinished work resumes when the issuer restores the achievement; learners are not silently moved to a different set of criteria.

Archive and restore increment the optimistic version and reject stale or repeated transitions with 409. Submission, resubmission, first issuance and enrollment transactions lock the affected achievement rows against archival. A concurrent request either completes before archival or observes the pause; it cannot store new work after archival commits without passing the state check. All states persist through restart.

This policy is informed by [Credly's template archival guidance](https://credlyissuer.zendesk.com/hc/en-us/articles/360027660052-Understanding-an-archived-template) and [Instructure's badge administration guidance](https://www.instructure.com/resources/webinars/canvas-credentials-catalog-beginning-year-admin-best-practices). Campus defines the pending-work and pathway pause rules above; they are not claims about those services' internal behavior.

## Next editions

Reviewers can choose **Next edition** on a published or archived achievement. The authoring dialog starts with the source name and criteria; change them for the new edition and save. This creates a private draft with a new ID and an immutable `predecessorId`. Publish it through the normal draft workflow when ready. Learners can expand **Previous edition criteria** to inspect the published source, including an archived source.

`POST /api/achievements/{id}/successors` accepts `name`, `criteria` and the source's `expectedVersion`, and returns 201 with a reviewer-only Location. The source must have been published. A private source or stale version returns 409; invalid content returns 400. Creating a successor does not change the source version or archive it. Multiple intentional successor drafts are allowed.

Editing, publishing and archiving the new edition preserve its predecessor relationship. Original evidence, reviews, signed credentials, pathway requirements and enrollments retain their original IDs. A badge for the new edition does not satisfy a pathway that requires the old edition. Reviewers must explicitly create any new pathway for a new cohort; there is no automatic migration.
