ALTER TABLE achievements ADD COLUMN published boolean NOT NULL DEFAULT true;
ALTER TABLE achievements ALTER COLUMN published SET DEFAULT false;
