# Evidence review

`EvidenceSubmission` represents a learner's evidence for an achievement. It is immutable and has three review states:

| State | Allowed operation | Result |
|---|---|---|
| Pending | Approve | Approved, with reviewer identity and review time |
| Pending | Reject with a reason | Rejected, with reviewer identity, time and reason |
| Rejected | Owner resubmits corrected evidence | Pending, with the revision incremented and prior review cleared |
| Approved | Require approval for issuance | Succeeds |

All other transitions are rejected. Approval is required before credential issuance can proceed. Authentication and authorization to act as a reviewer are responsibilities of the application boundary.

## Validation

- Evidence contains 1–4,000 characters after trimming.
- Rejection reasons contain 1–1,000 characters after trimming.
- A learner cannot review their own submission.
- A review cannot precede submission.
- Resubmission is restricted to the owner and cannot precede the rejection.
- Approved submissions cannot be rewritten or reviewed again.

Transitions return new instances; existing instances retain their original state. The submission ID, learner ID and achievement ID remain stable across revisions.

## Code boundaries

The model lives in `work.brodykim.campus.domain` and imports only standard-library types. A Konsist test enforces the domain import boundary. The Gradle `check` task includes tests and a Kover minimum line-coverage threshold of 90%.
