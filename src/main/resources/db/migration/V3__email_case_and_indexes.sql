-- Emails are login usernames, so uniqueness must ignore case.
-- Drop the old constraint first: if two emails differ only by case, the unique index below
-- then fails with a clear duplicate-key error instead of the UPDATE failing on the old constraint.
ALTER TABLE member DROP CONSTRAINT uk_member_email;
UPDATE member SET email = lower(trim(email));
CREATE UNIQUE INDEX ux_member_email_lower ON member (lower(email));

-- Foreign key column used by existsByBookId (deleting a book) and loan lookups by book.
CREATE INDEX ix_loan_book ON loan (book_id);
