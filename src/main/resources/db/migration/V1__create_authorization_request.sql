CREATE TABLE authorization_request (
    id           UUID PRIMARY KEY,
    type         VARCHAR(16)  NOT NULL,
    requested_by VARCHAR(255) NOT NULL,
    requested_at TIMESTAMPTZ  NOT NULL,
    subject      VARCHAR(255) NOT NULL,
    description  TEXT         NOT NULL,
    employee_id  VARCHAR(255),
    first_name   VARCHAR(255),
    last_name    VARCHAR(255),
    email        VARCHAR(255),
    department   VARCHAR(255),
    start_date   DATE
);

CREATE TABLE authorization_request_item (
    request_id         UUID         NOT NULL REFERENCES authorization_request (id) ON DELETE CASCADE,
    authorization_code VARCHAR(255) NOT NULL
);

CREATE INDEX authorization_request_item_request_id_idx ON authorization_request_item (request_id);
