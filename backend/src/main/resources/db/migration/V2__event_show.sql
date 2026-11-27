CREATE TABLE events (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL CHECK (length(btrim(name)) > 0),
    description VARCHAR(5000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE shows (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(id),
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    venue VARCHAR(200) NOT NULL CHECK (length(btrim(venue)) > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'CANCELLED')),
    sales_opened_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_show_publish_timestamp CHECK (status <> 'PUBLISHED' OR sales_opened_at IS NOT NULL)
);
CREATE INDEX ix_shows_event_id ON shows (event_id);
