# Changelog

All notable changes to this project are documented here. The format follows Keep a Changelog; versions follow semantic versioning.

## [0.1.0] - Unreleased

### Changed
- Switch the optional qbit-email integration to its published `com.kingsrook.qbits.email.api` package in `0.1.0-RC.2`, preserving templates, categories, idempotency keys, linked records and outcome mapping. Narrow the architecture allowance to that API package while retaining all guardrails.
- Email goes through qbit-email when it is produced in the instance (optional dependency, `qbit-email.version` 0.1.0-RC.2): alert channel emails (template `alerts_alert`), notification emails (`alerts_notification`) and digests (`alerts_digest`), all NOTIFICATION, one recipient per message, with idempotency keys and the alert or notification as the linked record. `messagingProviderName` stays as the fallback when qbit-email is absent, and is not required when it is present.
- Target released QQQ `4.1.0-RC.6`; verify normally with `./mvnw -B verify` (no version override).
- Adopt `qbit-build-parent:2.1.0` with architecture guardrail enforcement and strict UI API routes enabled for tests. Dependency, extension contract and stand-in checks run in the normal build.
- Add record followers to alert routing through core `RecordFollowers.get(instance, ref, FollowEvent.ALERT)` (#16, QRun-IO/qqq#1164). Deduplicate by user/channel with subscriptions and other record refs; require source-record read access under the follower's session, including record locks. Followers use the existing alert visibility, preferences and suppression pipeline without a dependency on qbit-collaboration. Missing/disabled/failing providers preserve subscription routing.
- Register six JSON UI specs as extension `alerts` at the established `/alerts` prefix, preserving methods, paths and existing payload fields. Publish the OpenAPI fragment with a regeneration/contract test and strict server coverage (#13, F2).
- Add summary `inboxHref`, notification operation actions, alert process actions with complete hrefs, and record-panel `follow.available` (#13, F20). Follow discovery reads core extension metadata without a collaboration dependency or URL.
- Remove the record-panel watch route and handler. Existing RECORD subscriptions and the deprecated `watching` response remain readable without a migration; new panel follows belong to collaboration (#16, F12).
- Move Alertmanager ingest to `/qqq/hooks/alerts/alertmanager`. The old path is an opt-in alias (`legacyAlertmanagerPath`, default false) for one minor release, incompatible with strict raw-route mode. Each alias call carries deprecation/sunset headers and logs a warning; authentication is shared (#14, F7).
- Run Checkstyle 14.3.0 in Maven validate (including tests); align the license template with this repository's existing headers and correct pre-existing switch/import formatting so the style check passes.
- Alert types and sink registration use core's alerts SPI (introduced in QQQ 4.1.0-RC.5); host `QAlerts.raise/resolve/heartbeat` calls reach the installed qbit through core instance metadata (qbit-alerts#15, F3).
- **Breaking:** remove the entire `com.kingsrook.qbits.alerts.spi` package (`NotificationSignal`, `NotificationAudience`, `NotificationCategory`, `NotificationSeverity`, `AlertLink`, package metadata) and `AlertsRuntime.sink().notify(...)`. Producers must use core RC.6 types and `QAlerts.notify`; rebuild existing callers. qbit-easypost-tracking#16 moved to core.
- Implement core `AlertSinkInterface.notify` in the registered adapter and runtime queue, retaining notification inbox, audience resolution, category preferences and email delivery behavior (qbit-alerts#15 part 2, F3/F12; QRun-IO/qqq#1125). Notification calls now receive core commit/rollback guarantees. No table or HTTP wire changes in this migration.
- Webhook signing delegates to core `WebhookSigner`/`WebhookSecret` (qbit-alerts#15, F11). Headers, `v1,` signatures and `whsec_` encoding are unchanged. A thin adapter preserves legacy keys shorter than core's 16-byte minimum using equivalent HMAC zero-padding.

### Added
- Apply reminders and escalation (#27, part 2): a minute-based hidden job sends due reminders, advances ordered USER/TEAM/CHANNEL escalation steps, and caps final-step repeats. Acknowledgement, snooze, resolution and mutes stop both; reopening restarts escalation. Delivery, events and progress commit together, with a persisted same-minute checkpoint.
- Add admin escalation policy and step tables, child step lists, and type/group policy links in guarded Liquibase changesets 23–26. Type policies override group policies. CRITICAL reminders default to 60 minutes; explicit zero turns them off.
- Keep EXPIRED reserved and remove it from selectable resolution values because the design specifies no expiration rule.
- Optional external channels:
  - Slack (an Incoming Webhook, Block Kit)
  - Microsoft Teams (a Workflows webhook, an Adaptive Card)
  - a generic webhook, signed with Standard Webhooks headers through core's `WebhookSigner` (QRun-IO/qqq#1109)
  - PagerDuty (Events API v2: trigger, acknowledge and resolve)
  - an Alertmanager sink (`/api/v2/alerts`, with the qqq labels and the origin guard)
- Prometheus Alertmanager ingest at `POST /qqq/hooks/alerts/alertmanager`. It takes a bearer token, creates EXTERNAL types with notifications off, and keeps a loop guard.
- Tables (`alerts_*`), with the Liquibase changelog `db/liquibase/qbit-alerts/alerts.changelog.sql`:
  - alerts, their timeline and records
  - types, groups and monitors
  - subscriptions, user settings, teams and members
  - channels, mutes and maintenance windows
  - the notification log, inbox items and heartbeats
- An engine with dedup, refresh, severity changes, snooze, resolve, a reopen window, flapping, quiet-period auto-resolve and maintenance windows.
- Monitors: `TABLE_QUERY`, `AGGREGATE`, `HEARTBEAT`, `PROCESS_FAILED` and `CODE`. They have:
  - a full-state diff, so a clean check resolves the monitor's open alerts
  - `forMinutes`
  - timeouts
  - `CHECK_FAILED`, which raises the meta-alert `alerts.monitor.failing`
  - seeded default monitors
- Routing:
  - subscriptions by scope, team expansion, the bootstrap rule and a recipient visibility check
  - suppression reasons logged on the notification
  - in-app (inbox), email and email-list channels
  - a pluggable channel SPI
  - retries with backoff to DEAD, and channel health
  - a redactor and detail levels for every external message
- Record actions, each a streamed ETL process:
  - on alerts: acknowledge, snooze, resolve, reopen, assign, comment and mute
  - on types, groups and monitors: subscribe and mute
  - on monitors: run check now, enable and disable
  - on types: create monitor
  - on channels: send test notification
  - on notifications: retry delivery
- Two hidden scheduled processes, for evaluation and delivery.
- The top-level Alerts app and its home dashboard, plus `AlertSummaryLoader` for link-only summaries.
- `/qqq/v1/alerts` routes for the Next UI bell and record panel.
- Record security, through three key types (`alerts_user`, `alerts_permission`, `alerts_readable_table`).
- Core alert producer SPI integration (QRun-IO/qqq#1108), plus temporary notification SPI compatibility until RC.6 (QRun-IO/qqq#1125).
- Repository scaffold: `AlertsQBitConfig` and `AlertsQBitProducer` (table prefix `alerts_`, permissions `alerts.*`), build, CI and repository standard files.
