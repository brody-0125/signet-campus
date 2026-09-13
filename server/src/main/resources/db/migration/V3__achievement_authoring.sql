ALTER TABLE achievements ADD COLUMN version bigint NOT NULL DEFAULT 0 CHECK (version >= 0);
ALTER TABLE achievements ADD CONSTRAINT achievement_name_length CHECK (length(trim(name)) BETWEEN 1 AND 120);
ALTER TABLE achievements ADD CONSTRAINT achievement_criteria_length CHECK (length(trim(criteria)) BETWEEN 1 AND 5000);
CREATE INDEX submissions_achievement_idx ON submissions (achievement_id);
