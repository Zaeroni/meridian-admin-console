-- Meridian Admin Console: core schema.
-- Enum-like columns are VARCHAR with CHECK constraints so the database itself rejects bad values.

CREATE TABLE reviewers (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    role          VARCHAR(20)  NOT NULL
        CONSTRAINT reviewers_role_check CHECK (role IN ('REVIEWER', 'OPERATIONS_LEAD', 'READ_ONLY')),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE pharmacies (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_name         VARCHAR(200) NOT NULL,
    owner_name            VARCHAR(120) NOT NULL,
    owner_email           VARCHAR(255) NOT NULL,
    phone                 VARCHAR(30),
    address_line          VARCHAR(200) NOT NULL,
    city                  VARCHAR(100) NOT NULL,
    state                 VARCHAR(2)   NOT NULL,
    postal_code           VARCHAR(10)  NOT NULL,
    license_number        VARCHAR(40)  NOT NULL,
    license_state         VARCHAR(2)   NOT NULL,
    license_holder_name   VARCHAR(200) NOT NULL,
    license_expiration    DATE         NOT NULL,
    delivery_radius_miles NUMERIC(5, 1)
        CONSTRAINT pharmacies_radius_check CHECK (delivery_radius_miles IS NULL OR delivery_radius_miles >= 0),
    storefront_name       VARCHAR(120) NOT NULL,
    storefront_slug       VARCHAR(80)  NOT NULL UNIQUE,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- One application per pharmacy. The status lives here, so there is exactly one state machine.
CREATE TABLE applications (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pharmacy_id          UUID        NOT NULL UNIQUE REFERENCES pharmacies (id) ON DELETE CASCADE,
    status               VARCHAR(20) NOT NULL
        CONSTRAINT applications_status_check CHECK (status IN
            ('SUBMITTED', 'IN_REVIEW', 'NEEDS_MORE_INFO', 'APPROVED', 'REJECTED', 'LIVE', 'SUSPENDED')),
    assigned_reviewer_id UUID REFERENCES reviewers (id),
    submitted_at         TIMESTAMPTZ NOT NULL,
    status_changed_at    TIMESTAMPTZ NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_applications_status ON applications (status);
CREATE INDEX idx_applications_status_changed_at ON applications (status_changed_at);

-- Document metadata only. Files are not stored because the project uses synthetic data.
CREATE TABLE documents (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID         NOT NULL REFERENCES applications (id) ON DELETE CASCADE,
    doc_type       VARCHAR(30)  NOT NULL
        CONSTRAINT documents_type_check CHECK (doc_type IN
            ('PHARMACY_LICENSE', 'BUSINESS_REGISTRATION', 'OWNER_ID')),
    file_name      VARCHAR(255) NOT NULL,
    content_type   VARCHAR(100) NOT NULL,
    size_bytes     BIGINT       NOT NULL CHECK (size_bytes > 0),
    uploaded_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_documents_application ON documents (application_id);

CREATE TABLE checklist_items (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID         NOT NULL REFERENCES applications (id) ON DELETE CASCADE,
    check_key      VARCHAR(40)  NOT NULL,
    label          VARCHAR(200) NOT NULL,
    required       BOOLEAN      NOT NULL DEFAULT TRUE,
    automatic      BOOLEAN      NOT NULL,
    result         VARCHAR(10)  NOT NULL DEFAULT 'PENDING'
        CONSTRAINT checklist_result_check CHECK (result IN ('PENDING', 'PASS', 'FAIL')),
    detail         VARCHAR(500),
    checked_by     UUID REFERENCES reviewers (id),
    checked_at     TIMESTAMPTZ,
    UNIQUE (application_id, check_key)
);

-- Append-only record of every status change. Never updated or deleted by the application.
CREATE TABLE status_history (
    id             BIGSERIAL PRIMARY KEY,
    application_id UUID        NOT NULL REFERENCES applications (id) ON DELETE CASCADE,
    from_status    VARCHAR(20),
    to_status      VARCHAR(20) NOT NULL,
    actor_id       UUID REFERENCES reviewers (id),
    reason         VARCHAR(1000),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_status_history_application ON status_history (application_id, created_at);

-- Orders exist so the platform overview has something to count. Seeded with synthetic data.
CREATE TABLE orders (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pharmacy_id UUID        NOT NULL REFERENCES pharmacies (id) ON DELETE CASCADE,
    total_cents INTEGER     NOT NULL CHECK (total_cents >= 0),
    placed_at   TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_orders_placed_at ON orders (placed_at);
