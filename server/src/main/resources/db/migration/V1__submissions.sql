CREATE TABLE achievements (
    id uuid PRIMARY KEY,
    name text NOT NULL,
    criteria text NOT NULL
);

INSERT INTO achievements VALUES (
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    'Digital Accessibility Awareness',
    'Submit a digital content accessibility audit covering keyboard navigation and text alternatives.'
);

CREATE TABLE submissions (
    id uuid PRIMARY KEY,
    learner_id uuid NOT NULL,
    achievement_id uuid NOT NULL REFERENCES achievements(id),
    evidence text NOT NULL CHECK (length(trim(evidence)) BETWEEN 1 AND 4000),
    submitted_at timestamptz NOT NULL,
    status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    reviewer_id uuid,
    reviewed_at timestamptz,
    reason text,
    revision integer NOT NULL DEFAULT 0 CHECK (revision >= 0),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    CHECK (reviewer_id IS NULL OR reviewer_id <> learner_id),
    CHECK (reviewed_at IS NULL OR reviewed_at >= submitted_at),
    CHECK ((status = 'PENDING' AND reviewer_id IS NULL AND reviewed_at IS NULL AND reason IS NULL)
        OR (status = 'APPROVED' AND reviewer_id IS NOT NULL AND reviewed_at IS NOT NULL AND reason IS NULL)
        OR (status = 'REJECTED' AND reviewer_id IS NOT NULL AND reviewed_at IS NOT NULL AND reason IS NOT NULL
            AND length(trim(reason)) BETWEEN 1 AND 1000))
);
CREATE INDEX submissions_learner ON submissions (learner_id);
CREATE INDEX submissions_pending ON submissions (submitted_at, id) WHERE status = 'PENDING';

CREATE TABLE submission_audit (
    submission_id uuid NOT NULL REFERENCES submissions(id),
    version bigint NOT NULL,
    snapshot jsonb NOT NULL,
    recorded_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (submission_id, version)
);
