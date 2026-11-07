-- TokenRouter schema. PostgreSQL is authoritative; Flyway owns migrations.

CREATE TABLE companies (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(255)  NOT NULL,
    prefix         VARCHAR(64)   NOT NULL UNIQUE,
    credit_limit   NUMERIC(18,6) NOT NULL DEFAULT 0,
    credit_spend   NUMERIC(18,6) NOT NULL DEFAULT 0,
    has_credits    BOOLEAN GENERATED ALWAYS AS (credit_spend > -credit_limit) STORED,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT companies_credit_limit_non_negative CHECK (credit_limit >= 0)
);

CREATE TABLE users (
    id         BIGSERIAL PRIMARY KEY,
    company_id BIGINT       NOT NULL REFERENCES companies (id),
    login      VARCHAR(255) NOT NULL,
    name       VARCHAR(255) NOT NULL,
    pwd_hash   TEXT         NOT NULL,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT users_company_login_unique UNIQUE (company_id, login),
    CONSTRAINT users_id_company_unique UNIQUE (id, company_id)
);

CREATE INDEX idx_users_company ON users (company_id);

CREATE TABLE api_tokens (
    id           BIGSERIAL PRIMARY KEY,
    company_id   BIGINT      NOT NULL REFERENCES companies (id),
    user_id      BIGINT      NOT NULL,
    token_hash   VARCHAR(64) NOT NULL,
    token_prefix VARCHAR(16) NOT NULL,
    revoked_at   TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT api_tokens_token_hash_unique UNIQUE (token_hash),
    CONSTRAINT api_tokens_id_company_unique UNIQUE (id, company_id),
    CONSTRAINT api_tokens_token_hash_format CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT api_tokens_user_company_fk
        FOREIGN KEY (user_id, company_id) REFERENCES users (id, company_id)
);

CREATE INDEX idx_api_tokens_company ON api_tokens (company_id);
CREATE INDEX idx_api_tokens_user ON api_tokens (user_id);

CREATE TABLE models (
    id                       BIGSERIAL PRIMARY KEY,
    code_name                VARCHAR(128) NOT NULL UNIQUE,
    provider_url             TEXT         NOT NULL,
    price_per_m_input        NUMERIC(18,6) NOT NULL,
    price_per_m_output       NUMERIC(18,6) NOT NULL,
    default_price_quantifier NUMERIC(9,4)  NOT NULL DEFAULT 1.0,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT models_price_input_non_negative CHECK (price_per_m_input >= 0),
    CONSTRAINT models_price_output_non_negative CHECK (price_per_m_output >= 0),
    CONSTRAINT models_quantifier_non_negative CHECK (default_price_quantifier >= 0)
);

CREATE TABLE company_model_prices (
    company_id         BIGINT        NOT NULL REFERENCES companies (id),
    model_id           BIGINT        NOT NULL REFERENCES models (id),
    price_per_m_input  NUMERIC(18,6) NOT NULL,
    price_per_m_output NUMERIC(18,6) NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (company_id, model_id),
    CONSTRAINT company_model_prices_input_non_negative CHECK (price_per_m_input >= 0),
    CONSTRAINT company_model_prices_output_non_negative CHECK (price_per_m_output >= 0)
);

CREATE TABLE usage_rollup (
    company_id           BIGINT      NOT NULL,
    api_token_id         BIGINT      NOT NULL,
    provider             VARCHAR(32) NOT NULL,
    model                VARCHAR(128) NOT NULL,
    window_start         TIMESTAMPTZ NOT NULL,
    input_tokens         BIGINT      NOT NULL,
    output_tokens        BIGINT      NOT NULL,
    total_tokens         BIGINT GENERATED ALWAYS AS (input_tokens + output_tokens) STORED,
    priced_input_tokens  BIGINT      NOT NULL DEFAULT 0,
    priced_output_tokens BIGINT      NOT NULL DEFAULT 0,
    needs_pricing        BOOLEAN GENERATED ALWAYS AS (
        input_tokens > priced_input_tokens OR output_tokens > priced_output_tokens
    ) STORED,
    last_priced_at       TIMESTAMPTZ,
    PRIMARY KEY (company_id, api_token_id, provider, model, window_start),
    CONSTRAINT usage_rollup_company_fk
        FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT usage_rollup_token_company_fk
        FOREIGN KEY (api_token_id, company_id) REFERENCES api_tokens (id, company_id),
    CONSTRAINT usage_rollup_tokens_non_negative
        CHECK (input_tokens >= 0 AND output_tokens >= 0),
    CONSTRAINT usage_rollup_minute_aligned
        CHECK (date_bin('1 minute', window_start, 'epoch'::timestamptz) = window_start),
    CONSTRAINT usage_rollup_provider_valid
        CHECK (provider IN ('OPENAI', 'ANTHROPIC', 'DEEPSEEK'))
);

CREATE INDEX idx_usage_rollup_company_time
    ON usage_rollup (company_id, window_start);
CREATE INDEX idx_usage_rollup_token_time
    ON usage_rollup (api_token_id, window_start);
CREATE INDEX idx_usage_rollup_model_time
    ON usage_rollup (model, window_start);
CREATE INDEX idx_usage_rollup_needs_pricing
    ON usage_rollup (window_start)
    WHERE needs_pricing;

CREATE TABLE usage_pricing_runs (
    id            BIGSERIAL PRIMARY KEY,
    company_id    BIGINT        NOT NULL REFERENCES companies (id),
    window_from   TIMESTAMPTZ   NOT NULL,
    window_to     TIMESTAMPTZ   NOT NULL,
    input_tokens  BIGINT        NOT NULL,
    output_tokens BIGINT        NOT NULL,
    total_tokens  BIGINT        NOT NULL,
    input_cost    NUMERIC(18,6) NOT NULL,
    output_cost   NUMERIC(18,6) NOT NULL,
    total_cost    NUMERIC(18,6) NOT NULL,
    status        VARCHAR(16)   NOT NULL DEFAULT 'DONE',
    error_message TEXT,
    started_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    finished_at   TIMESTAMPTZ,
    CONSTRAINT usage_pricing_runs_status_valid
        CHECK (status IN ('RUNNING', 'DONE', 'FAILED')),
    CONSTRAINT usage_pricing_runs_window_valid CHECK (window_to > window_from)
);

CREATE TABLE usage_cost (
    id                         BIGSERIAL PRIMARY KEY,
    company_id                 BIGINT        NOT NULL,
    api_token_id               BIGINT        NOT NULL,
    provider                   VARCHAR(32)   NOT NULL,
    model                      VARCHAR(128)  NOT NULL,
    window_start               TIMESTAMPTZ   NOT NULL,
    model_id                   BIGINT        NOT NULL REFERENCES models (id),
    price_source               VARCHAR(8)    NOT NULL,
    input_tokens_delta         BIGINT        NOT NULL,
    output_tokens_delta        BIGINT        NOT NULL,
    total_tokens_delta         BIGINT GENERATED ALWAYS AS (
        input_tokens_delta + output_tokens_delta
    ) STORED,
    input_price_per_m          NUMERIC(18,6) NOT NULL,
    output_price_per_m         NUMERIC(18,6) NOT NULL,
    input_cost                 NUMERIC(18,6) NOT NULL,
    output_cost                NUMERIC(18,6) NOT NULL,
    total_cost                 NUMERIC(18,6) GENERATED ALWAYS AS (
        input_cost + output_cost
    ) STORED,
    provider_input_price_per_m NUMERIC(18,6) NOT NULL,
    provider_output_price_per_m NUMERIC(18,6) NOT NULL,
    provider_input_cost        NUMERIC(18,6) NOT NULL,
    provider_output_cost       NUMERIC(18,6) NOT NULL,
    provider_total_cost        NUMERIC(18,6) GENERATED ALWAYS AS (
        provider_input_cost + provider_output_cost
    ) STORED,
    priced_at                  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    pricing_run_id             BIGINT        NOT NULL REFERENCES usage_pricing_runs (id),
    CONSTRAINT usage_cost_company_fk
        FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT usage_cost_token_fk
        FOREIGN KEY (api_token_id) REFERENCES api_tokens (id),
    CONSTRAINT usage_cost_rollup_fk
        FOREIGN KEY (company_id, api_token_id, provider, model, window_start)
        REFERENCES usage_rollup (company_id, api_token_id, provider, model, window_start),
    CONSTRAINT usage_cost_deltas_non_negative
        CHECK (input_tokens_delta >= 0 AND output_tokens_delta >= 0),
    CONSTRAINT usage_cost_price_source_valid
        CHECK (price_source IN ('COMPANY', 'GLOBAL'))
);

CREATE INDEX idx_usage_cost_company_time
    ON usage_cost (company_id, window_start);
CREATE INDEX idx_usage_cost_usage_key
    ON usage_cost (company_id, api_token_id, provider, model, window_start);

CREATE TABLE company_pricing_state (
    company_id                BIGINT PRIMARY KEY REFERENCES companies (id),
    last_priced_window_start  TIMESTAMPTZ,
    last_priced_at            TIMESTAMPTZ,
    last_pricing_run_id       BIGINT REFERENCES usage_pricing_runs (id)
);

CREATE TABLE credit_ledger (
    id             BIGSERIAL PRIMARY KEY,
    company_id     BIGINT        NOT NULL REFERENCES companies (id),
    entry_type     VARCHAR(16)   NOT NULL,
    amount         NUMERIC(18,6) NOT NULL,
    pricing_run_id BIGINT REFERENCES usage_pricing_runs (id),
    note           TEXT,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by     BIGINT REFERENCES users (id),
    CONSTRAINT credit_ledger_entry_type_valid
        CHECK (entry_type IN ('TOPUP', 'USAGE')),
    CONSTRAINT credit_ledger_semantics CHECK (
        (entry_type = 'TOPUP' AND amount > 0 AND created_by IS NOT NULL AND pricing_run_id IS NULL)
        OR
        (entry_type = 'USAGE' AND amount < 0 AND pricing_run_id IS NOT NULL AND created_by IS NULL)
    )
);

CREATE UNIQUE INDEX credit_ledger_usage_run_uq
    ON credit_ledger (pricing_run_id)
    WHERE entry_type = 'USAGE';
CREATE INDEX idx_credit_ledger_company_time
    ON credit_ledger (company_id, created_at);

CREATE TABLE aggregation_checkpoint (
    id                SMALLINT PRIMARY KEY DEFAULT 1,
    last_window_start TIMESTAMPTZ,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT aggregation_checkpoint_singleton CHECK (id = 1)
);

INSERT INTO aggregation_checkpoint (id) VALUES (1) ON CONFLICT DO NOTHING;

-- Laravel-style updated_at maintenance.
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER companies_set_updated_at
    BEFORE UPDATE ON companies
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER users_set_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER models_set_updated_at
    BEFORE UPDATE ON models
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER company_model_prices_set_updated_at
    BEFORE UPDATE ON company_model_prices
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
