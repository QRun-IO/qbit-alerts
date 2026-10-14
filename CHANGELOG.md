# Changelog

All notable changes to this project are documented here. The format follows Keep a Changelog; versions follow semantic versioning.

## [0.1.0] - Unreleased

### Added
- Notifications user-preferences section over the existing settings and subscription tables, with user/client-channel audits, category severity controls, ownership validation, and an inbox `preferencesSection` shortcut. Enabled by default when core preferences metadata is available; configurable with `withPreferencesSectionEnabled`.
- Nullable in-app notification severity threshold (`qbit-alerts:21`); SECURITY always arrives in-app. Notification email pauses now defer non-security immediate and digest delivery without losing older pending items.
- Optional external channels:
  - Slack (an Incoming Webhook, Block Kit)
  - Microsoft Teams (a Workflows webhook, an Adaptive Card)
  - a generic webhook, signed with Standard Webhooks headers through a stand-in for core's `WebhookSigner` (QRun-IO/qqq#1109)
  - PagerDuty (Events API v2: trigger, acknowledge and resolve)
  - an Alertmanager sink (`/api/v2/alerts`, with the qqq labels and the origin guard)
- Prometheus Alertmanager ingest at `POST /qqq/v1/alerts/ingest/alertmanager`. It takes a bearer token, creates EXTERNAL types with notifications off, and keeps a loop guard.
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
- A stand-in for the core producer SPI (QRun-IO/qqq#1108), to be removed when that RC ships.
- Repository scaffold: `AlertsQBitConfig` and `AlertsQBitProducer` (table prefix `alerts_`, permissions `alerts.*`), build, CI and repository standard files.
