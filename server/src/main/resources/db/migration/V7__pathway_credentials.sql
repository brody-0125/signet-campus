ALTER TABLE credentials ALTER COLUMN submission_id DROP NOT NULL;
ALTER TABLE credentials ADD COLUMN pathway_id uuid;
ALTER TABLE credentials ADD CONSTRAINT credentials_source CHECK ((submission_id IS NULL) <> (pathway_id IS NULL));
ALTER TABLE credentials ADD CONSTRAINT credentials_pathway_enrollment
    FOREIGN KEY (pathway_id, learner_id) REFERENCES pathway_enrollments(pathway_id, learner_id);
ALTER TABLE credentials ADD CONSTRAINT credentials_pathway_learner UNIQUE (pathway_id, learner_id);
