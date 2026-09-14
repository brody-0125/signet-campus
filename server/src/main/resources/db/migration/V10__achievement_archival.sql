ALTER TABLE achievements ADD COLUMN archived boolean NOT NULL DEFAULT false;
ALTER TABLE achievements ADD CONSTRAINT archived_achievement_published CHECK (NOT archived OR published);
