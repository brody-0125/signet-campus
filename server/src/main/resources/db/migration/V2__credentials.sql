CREATE TABLE credentials (
    id uuid PRIMARY KEY,
    submission_id uuid NOT NULL UNIQUE REFERENCES submissions(id),
    learner_id uuid NOT NULL,
    document jsonb NOT NULL CHECK (jsonb_typeof(document) = 'object'),
    issued_at timestamptz NOT NULL,
    valid_until timestamptz NOT NULL CHECK (valid_until > issued_at),
    revoked_at timestamptz,
    revoked_by uuid,
    CHECK ((revoked_at IS NULL) = (revoked_by IS NULL))
);
CREATE INDEX credentials_learner ON credentials (learner_id);
