# Submission API

Requests use JSON. The achievement catalog is public; submission endpoints require an OIDC bearer token with audience `signet-campus`. The token subject identifies the learner; clients cannot assign submission ownership.

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/achievements` | List available achievement criteria |
| POST | `/api/submissions` | Submit evidence; returns 201 |
| GET | `/api/submissions?offset=0&limit=50` | List own submissions; reviewers receive pending submissions |
| GET | `/api/submissions/{id}` | Read evidence as its owner or a reviewer |
| POST | `/api/submissions/{id}/approve` | Approve as a reviewer |
| POST | `/api/submissions/{id}/reject` | Reject with a reason as a reviewer |
| POST | `/api/submissions/{id}/resubmit` | Replace rejected evidence as its owner |

## Submit

List requests accept a non-negative offset and a limit from 1 to 100. Results use submission time and ID ordering.

```json
{
  "achievementId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
  "evidence": "Keyboard navigation audit and remediation evidence"
}
```

The achievement must exist. The response contains `submission` and `version`. The submission includes its ID, learner and achievement IDs, evidence, submission time, review state and revision.

## Review and resubmit

An approval request contains `expectedVersion`. Rejection additionally requires `reason`; resubmission additionally requires `evidence`.

```json
{"expectedVersion": 0, "reason": "Include text alternative evidence"}
```

Use the version returned by the most recent read. Every successful write increments it. Updates compare the version atomically in PostgreSQL; a concurrent write causes a conflict. Review state and an audit snapshot commit together, and an audit failure rolls back the update. Audit snapshots include evidence and must follow the same access and retention policy as submissions.

## Errors

| Status | Meaning |
|---|---|
| 400 | Invalid evidence, unknown achievement, self-review or invalid request |
| 401 | Missing or invalid bearer token |
| 403 | Reviewer permission required |
| 404 | Submission is absent or inaccessible |
| 409 | Stale version or invalid state transition |

Application errors use Problem Details responses without SQL diagnostics or private evidence. See [Evidence review](EVIDENCE_REVIEW.md) for transition rules and limits.
