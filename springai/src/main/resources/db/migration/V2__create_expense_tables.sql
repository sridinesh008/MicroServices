CREATE TABLE expense_batch (
    id            BIGSERIAL PRIMARY KEY,
    owner_id      BIGINT NOT NULL REFERENCES app_user (id),
    source_mode   VARCHAR(20) NOT NULL,
    raw_user_text TEXT,
    image_count   INT NOT NULL DEFAULT 0,
    submitted_at  TIMESTAMPTZ NOT NULL,
    llm_provider  VARCHAR(50),
    status        VARCHAR(30) NOT NULL
);

CREATE INDEX ix_expense_batch_owner ON expense_batch (owner_id);

CREATE TABLE expense (
    id                 BIGSERIAL PRIMARY KEY,
    owner_id           BIGINT NOT NULL REFERENCES app_user (id),
    batch_id           BIGINT REFERENCES expense_batch (id),
    category           VARCHAR(100) NOT NULL,
    amount             NUMERIC(12, 2) NOT NULL,
    description        TEXT,
    source_mode        VARCHAR(20) NOT NULL,
    status             VARCHAR(30) NOT NULL,
    expense_date       DATE NOT NULL,
    expense_year       INT NOT NULL,
    expense_month      INT NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    recategorized_at   TIMESTAMPTZ,
    original_category  VARCHAR(100)
);

CREATE INDEX ix_expense_owner_year_month ON expense (owner_id, expense_year, expense_month);
CREATE INDEX ix_expense_owner_status ON expense (owner_id, status);
CREATE INDEX ix_expense_owner_category ON expense (owner_id, category);
