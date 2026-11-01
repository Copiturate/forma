CREATE TABLE inquiries (
    id UUID PRIMARY KEY,
    client_name VARCHAR(80) NOT NULL,
    email VARCHAR(254) NOT NULL,
    service_code VARCHAR(24) NOT NULL,
    area INTEGER NOT NULL CHECK (area BETWEEN 20 AND 2000),
    message VARCHAR(2000) NOT NULL,
    estimate_min BIGINT NOT NULL,
    estimate_max BIGINT NOT NULL,
    pricing_version VARCHAR(24) NOT NULL,
    consent_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(24) NOT NULL CHECK (status IN ('NEW', 'REVIEWING', 'CONTACTED', 'CLOSED')),
    version BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_inquiries_created ON inquiries (created_at DESC);
CREATE INDEX idx_inquiries_status_created ON inquiries (status, created_at DESC);
