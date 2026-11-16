-- Apply to existing PostgreSQL databases whose features.description is varchar(4000).
ALTER TABLE features
    ALTER COLUMN description TYPE TEXT;
