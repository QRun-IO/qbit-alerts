-- liquibase formatted sql

-- qbit-alerts starter defaults: the two channels every install needs, and the
-- two groups the qbit itself files alerts under, so Delivery health and the
-- group lists are not empty before the first alert.
-- Include it from the host's master changelog AFTER alerts.changelog.sql:
--
--   - include: { file: db/liquibase/qbit-alerts/alerts-defaults.changelog.sql }
--
-- Rules for this file:
--   * insert-only: every row is inserted only when its natural key (the name) is
--     absent, so an administrator's edits always survive and nothing is updated;
--   * no secrets: the channels hold settings only, never a secret;
--   * portable SQL (PostgreSQL and H2): INSERT ... SELECT ... WHERE NOT EXISTS;
--   * every changeset has a precondition on its table, so a host that has not
--     created a table yet simply skips it (MARK_RAN).
-- The runtime creates the same rows when they are missing (AlertChannels.defaultChannel,
-- AlertTypeSync.ensureGroup); this file only makes them exist from the first deploy.
-- Alert types, monitors, escalation policies, teams and subscriptions are NOT seeded
-- here: types and default monitors are declared by the installed qbits and synced at
-- startup of the Alerts home, and the rest is the host's own people and policy.
-- AlertsDefaultsChangelogTest runs this file twice against a fresh schema.

-- changeset qbit-alerts:defaults-1
-- preconditions onFail:MARK_RAN onError:MARK_RAN
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM alerts_channel WHERE name = 'in-app'
-- comment: The in-app channel (the bell): every user has it, full detail, no rate limit.
INSERT INTO alerts_channel (name, label, channel_type, enabled, detail_level, min_severity, rate_limit_per_hour, health_status, consecutive_failures, create_date, modify_date)
SELECT 'in-app', 'In-App (Bell)', 'IN_APP', TRUE, 'FULL', 'INFO', 0, 'HEALTHY', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM alerts_channel WHERE name = 'in-app');
-- rollback DELETE FROM alerts_channel WHERE name = 'in-app';

-- changeset qbit-alerts:defaults-2
-- preconditions onFail:MARK_RAN onError:MARK_RAN
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM alerts_channel WHERE name = 'email'
-- comment: The personal email channel: a user's own address (My Alert Settings), minimal detail, 60 an hour.
INSERT INTO alerts_channel (name, label, channel_type, enabled, detail_level, min_severity, rate_limit_per_hour, health_status, consecutive_failures, create_date, modify_date)
SELECT 'email', 'Email (Personal Address)', 'EMAIL', TRUE, 'MINIMAL', 'INFO', 60, 'HEALTHY', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM alerts_channel WHERE name = 'email');
-- rollback DELETE FROM alerts_channel WHERE name = 'email';

-- changeset qbit-alerts:defaults-3
-- preconditions onFail:MARK_RAN onError:MARK_RAN
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM alerts_group WHERE name = 'alerts'
-- comment: The group the qbit files its own alerts under (a monitor that cannot run, a failing channel, dropped signals).
INSERT INTO alerts_group (name, label, create_date, modify_date)
SELECT 'alerts', 'Alerts', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM alerts_group WHERE name = 'alerts');
-- rollback DELETE FROM alerts_group WHERE name = 'alerts';

-- changeset qbit-alerts:defaults-4
-- preconditions onFail:MARK_RAN onError:MARK_RAN
-- precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM alerts_group WHERE name = 'other'
-- comment: The catch-all group for alert types that name no group (and the unregistered type).
INSERT INTO alerts_group (name, label, create_date, modify_date)
SELECT 'other', 'Other', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM alerts_group WHERE name = 'other');
-- rollback DELETE FROM alerts_group WHERE name = 'other';
