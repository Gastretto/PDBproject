CREATE TABLE companies (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    prefix      VARCHAR(64)  NOT NULL UNIQUE,
    token_limit BIGINT       NOT NULL DEFAULT 0,
    tokens_spent BIGINT      NOT NULL DEFAULT 0
);

CREATE TABLE users (
    id         BIGSERIAL PRIMARY KEY,
    company_id BIGINT      NOT NULL REFERENCES companies (id),
    login      VARCHAR(255) NOT NULL UNIQUE,
    name       VARCHAR(255) NOT NULL
);

CREATE TABLE api_keys (
    key        VARCHAR(80) PRIMARY KEY,
    company_id BIGINT      NOT NULL REFERENCES companies (id),
    user_id    BIGINT      NOT NULL REFERENCES users (id),
    active     BOOLEAN     NOT NULL DEFAULT TRUE
);
