CREATE TABLE pathway_prerequisites (
    pathway_id uuid NOT NULL REFERENCES pathways(id),
    achievement_id uuid NOT NULL REFERENCES achievements(id),
    position integer NOT NULL CHECK (position BETWEEN 0 AND 49),
    PRIMARY KEY (pathway_id, achievement_id),
    UNIQUE (pathway_id, position)
);
