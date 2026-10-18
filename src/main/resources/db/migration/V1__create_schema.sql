CREATE TABLE member (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_member_email UNIQUE (email)
);

CREATE TABLE book (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title             VARCHAR(255) NOT NULL,
    author            VARCHAR(255) NOT NULL,
    isbn              VARCHAR(20)  NOT NULL,
    total_copies      INT          NOT NULL,
    available_copies  INT          NOT NULL,
    version           BIGINT       NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_book_isbn UNIQUE (isbn),
    CONSTRAINT ck_book_total_copies CHECK (total_copies >= 0),
    CONSTRAINT ck_book_available_range
        CHECK (available_copies >= 0 AND available_copies <= total_copies)
);

CREATE TABLE loan (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    book_id      BIGINT      NOT NULL,
    member_id    BIGINT      NOT NULL,
    borrowed_at  TIMESTAMPTZ NOT NULL,
    due_date     TIMESTAMPTZ NOT NULL,
    returned_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_loan_book   FOREIGN KEY (book_id)   REFERENCES book (id)   ON DELETE RESTRICT,
    CONSTRAINT fk_loan_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT ck_loan_due_after_borrow CHECK (due_date > borrowed_at),
    CONSTRAINT ck_loan_returned_after_borrow
        CHECK (returned_at IS NULL OR returned_at >= borrowed_at)
);


CREATE INDEX ix_loan_member_active
    ON loan (member_id) WHERE returned_at IS NULL;

CREATE INDEX ix_loan_member_due_active
    ON loan (member_id, due_date) WHERE returned_at IS NULL;

CREATE UNIQUE INDEX ux_loan_active_member_book
    ON loan (member_id, book_id) WHERE returned_at IS NULL;