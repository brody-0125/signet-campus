ALTER TABLE achievements ADD COLUMN predecessor_id uuid REFERENCES achievements(id);
