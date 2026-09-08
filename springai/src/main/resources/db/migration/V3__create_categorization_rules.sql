CREATE TABLE categorization_rule (
    id               BIGSERIAL PRIMARY KEY,
    owner_id         BIGINT NOT NULL REFERENCES app_user (id),
    instruction_text TEXT NOT NULL,
    active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL,
    source_batch_id  BIGINT REFERENCES expense_batch (id)
);

CREATE INDEX ix_categorization_rule_owner_active ON categorization_rule (owner_id, active);
