CREATE TABLE notification_outbox (
    id uuid PRIMARY KEY,
    pathway_id uuid NOT NULL,
    learner_id uuid NOT NULL,
    recipient text,
    pathway_name text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    sent_at timestamptz,
    last_error text,
    UNIQUE (pathway_id, learner_id),
    FOREIGN KEY (pathway_id, learner_id) REFERENCES pathway_enrollments(pathway_id, learner_id),
    CHECK ((sent_at IS NULL AND recipient IS NOT NULL) OR (sent_at IS NOT NULL AND recipient IS NULL))
);
CREATE INDEX notification_outbox_due ON notification_outbox (next_attempt_at, id) WHERE sent_at IS NULL;
