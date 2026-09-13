CREATE TABLE pathways (
    id uuid PRIMARY KEY,
    name text NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 120),
    description text NOT NULL CHECK (length(trim(description)) BETWEEN 1 AND 5000)
);
CREATE TABLE pathway_requirements (
    pathway_id uuid NOT NULL REFERENCES pathways(id),
    achievement_id uuid NOT NULL REFERENCES achievements(id),
    position integer NOT NULL CHECK (position BETWEEN 0 AND 49),
    PRIMARY KEY (pathway_id, achievement_id),
    UNIQUE (pathway_id, position)
);
CREATE TABLE pathway_enrollments (
    pathway_id uuid NOT NULL REFERENCES pathways(id),
    learner_id uuid NOT NULL,
    enrolled_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (pathway_id, learner_id)
);
