-- SENTRY-327
ALTER TABLE SENTRY_DB_PRIVILEGE ADD COLUMN WITH_GRANT_OPTION CHAR(1) NOT NULL DEFAULT 'N';
