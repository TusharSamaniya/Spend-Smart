CREATE TABLE IF NOT EXISTS categories (
    id UUID PRIMARY KEY,
    org_id UUID,
    parent_id UUID REFERENCES categories(id),
    name VARCHAR(128) NOT NULL,
    level INT DEFAULT 1
);
