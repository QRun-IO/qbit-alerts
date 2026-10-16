# Changelog

All notable changes to this project are documented here. The format follows Keep a Changelog; versions follow semantic versioning.

## [0.1.0] - Unreleased

### Fixed
- Duplicate health alerts: a health check that failed again more than 30 minutes after its last resolve created a second alert and a second bell entry for the same problem. The reopen window is now per type: `alerts_type.reopenWindowMinutes` (changeset 55), else the longest matching prefix in `AlertsQBitConfig.reopenWindowMinutesByTypePrefix` (default `health.` = 360 minutes), else `reopenWindowMinutes` (30). A reopen updates the existing alert and its bell entry; flapping detection is unchanged.
- Bell noise: a resolve marked the alert's bell entry unread again. It now updates the entry in place (title "Resolved: ...") and leaves the read state alone. A subscription whose events name RESOLVED and differ from the default set (`AlertRouter.DEFAULT_SUBSCRIPTION_EVENTS`) keeps the old behavior. External channels are unchanged.
- Under OAuth2, per-recipient checks (alert visibility for external channels, follower record access, audience sessions) never worked: they used token-less automated sessions, which OAuth2 rejects, so external sends fell back to the conservative rule. When the host declares the QQQ `alerts` service principal (`AlertsQBitConfig.withServicePrincipal`, default `alerts`), these checks run on the recipient's behalf through it: the recipient's current access intersected with the principal's. Alert recording and routing bookkeeping stay on the system session, so alerting does not depend on the identity provider being up. With no principal declared, behavior is unchanged. Needs the QQQ release with service identities (qqq#1255, #1256).
- Audit noise: every monitor run (lease claim, then run result) and every reminder check wrote a "Record was Edited" audit (about 7,000 `alerts_monitor` and 1,800 `alerts_alert` audits in 6 hours on one production host, most with no field details). Engine updates that touch only bookkeeping fields (`RuntimeFields`: monitor `claimedBy`, `claimedUntil`, `lastRunAt`, `nextRunAt`, `lastStatus`, `lastValue`, `lastError`, `lastElapsedMs`, `consecutiveFailures`, `conditionSince`; alert `timedNotificationCheckedAt`, `lastNotifiedAt`, `recordRefCount`) are written with `omitDmlAudit`. Configuration edits, raises and alert state changes are audited as before, as are UI and API edits of any field.
- Read the QBit metadata version from the Maven-filtered artifact version so release builds report their release version.
- Record-view child lists (e.g. a notification's Recipients) failed with "Error in JSON Serialization": the child table's meta-data includes the qbit config, and `AlertsQBitConfig.getRuntime()` points at a runtime that points back at the config, so Jackson recursed to its 1000-level nesting limit. `getRuntime()` and `getAudienceResolver()` are now `@JsonIgnore`, as in the other qbits' configs. A regression test renders and serializes every child-list widget.

### Added
- Ship the full host-neutral Alerts help bundle through QQQ 4.1.0-RC.7 core registration, replacing the temporary loader. Cover owned tables, fields, actions, jobs, widgets and possible-value sources with a bundle coverage gate; support HOST overrides and hosts without help tables.
- Starter defaults changelog `alerts-defaults.changelog.sql` (insert-only, guarded): the `in-app` and `email` channels and the `alerts` and `other` groups, equal to what the runtime creates on first use. Tested through real Liquibase on H2.
- Help for the Alerts app and every `alerts_*` table (`help/qbit-alerts.yaml`): what the table is for and how its rows get created, shown when it is empty.
- A Setup card first on the Alerts home (alerts admins): each configuration table, its state and the next step.
- `AlertsTablesGuardrailTest`: every table is used by runtime code, seeded or listed as starting empty with a reason, and has help.

### Fixed
- A group's `ownerTeamId` was stored but never read. An alert that no subscription matches (WARNING and above) now goes to the owner team of its group, or of the nearest parent group that has one.
- A user's `defaultChannelIds` was stored but never read. It now adds the chosen external channels to the bell (unless In-App is off) for subscriptions that name none.
- Alert types, groups and default monitors also sync on the first bell request, so those tables are not empty on a running install.

### Changed
- Keep reminders and escalations immediate for digest subscribers while applying pause and quiet hours to their push channels. Preserve team audience resolution, mute scopes, both scheduled jobs and changesets 21-27.
- Apply user pause and local quiet hours to personal push channels (email, email lists and other non-IN_APP channels), including team members, with logged suppression reasons and a configurable quiet-hours severity bypass. The in-app bell remains unaffected, including digest delivery. Channel subscriptions are unaffected.
- Queue hourly and daily alert subscription digests and send them through the hidden hourly `alertsSendDigests` process. Group linked alert summaries into one message per user/channel/window, use the existing email integration, and keep durable dispatch claims in the Delivery Log (guarded changeset 27). Reruns cannot send a window twice; failed, interrupted and oversized windows remain visible for review.
- Team subscriptions now combine explicit members with ROLE and IDP_GROUP users from the optional public `AlertAudienceResolver` configured through `withAudienceResolver`. Recipients are deduplicated and retain per-user visibility checks. Missing resolvers resolve to nobody, log the reason, and show configuration-aware help on team/member screens.
- Fix the alert Mute action to target its subject record, or its type and dedup key when no subject exists. Add the separate "Mute this alert type" action. RECORD mutes now compare table and record ID. Guarded changesets 21–22 add `scope_record_id` and allow full ALERT keys; existing type-wide mutes retain their scope.
- Switch the optional qbit-email integration to its published `com.kingsrook.qbits.email.api` package in `0.1.0-RC.2`, preserving templates, categories, idempotency keys, linked records and outcome mapping. Narrow the architecture allowance to that API package while retaining all guardrails.
- Email goes through qbit-email when it is produced in the instance (optional dependency, `qbit-email.version` 0.1.0-RC.2): alert channel emails (template `alerts_alert`), notification emails (`alerts_notification`) and digests (`alerts_digest`), all NOTIFICATION, one recipient per message, with idempotency keys and the alert or notification as the linked record. `messagingProviderName` stays as the fallback when qbit-email is absent, and is not required when it is present.
- Target released QQQ `4.1.0-RC.7`; verify offline with `./mvnw -o -q verify` (no version override).
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
- Alerts UI API extension 1.1.0 adds nullable `preferencesSection` to the typed inbox summary alongside `inboxHref`, with a regenerated OpenAPI fragment. Slack category controls appear in Notifications preferences when the configured per-user Slack provider is registered; SECURITY Slack controls remain editable.
- Notifications user-preferences section over the existing settings and subscription tables, with user/client-channel audits, category in-app/email/Slack severity controls, ownership validation, and an inbox `preferencesSection` shortcut. Enabled by default when core preferences metadata is available; configurable with `withPreferencesSectionEnabled`.
- Nullable in-app notification severity threshold (`qbit-alerts:29`); SECURITY always arrives in-app. Notification email and per-user Slack pauses now defer non-security immediate and digest delivery without losing older pending items.
- Send notifications to Slack per user and category (QRun-IO/qbit-slack docs/DESIGN.md section 10; supersedes "Slack never per user"). `alerts_notification_preference` gets `slackMode` (OFF/IMMEDIATE/DAILY_DIGEST, default OFF) and `slackMinSeverity` (default WARNING); recipients get `slackStatus` and `slackMessageId` (guarded changeset 28). IMMEDIATE and daily-digest DMs go through core `SendMessageAction` (provider `AlertsQBitConfig.withSlackProviderName`, default `slack`, address `user:<id>`) with no qbit-slack dependency. Pause holds non-SECURITY messages; quiet hours hold them (CRITICAL bypasses), SECURITY carries title and link only, a user without a Slack connection is skipped, and failures never fail the notification. RC.6 has no idempotency key or `isAvailable`, so `slackStatus` guards resends (STAND-IN core#1194, removeBy RC.8) and availability checks the instance's provider.
- Apply reminders and escalation (#27, part 2): a minute-based hidden job sends due reminders, advances ordered USER/TEAM/CHANNEL escalation steps, and caps final-step repeats. Acknowledgement, snooze, resolution and mutes stop both; reopening restarts escalation. Delivery, events and progress commit together, with a persisted same-minute checkpoint.
- Add admin escalation policy and step tables, child step lists, and type/group policy links in guarded Liquibase changesets 23–26. Type policies override group policies. CRITICAL reminders default to 60 minutes; explicit zero turns them off.
- Keep EXPIRED reserved and remove it from selectable resolution values because the design specifies no expiration rule.
- Per-notification email opt-in via recipient email status `REQUESTED`: immediate delivery without a standing category preference. It still honours the category email minimum severity, the user pause (SECURITY bypasses) and quiet hours (held until they end, for up to a day), is skipped when no address is known, and is never emailed for ALERT notifications.
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
