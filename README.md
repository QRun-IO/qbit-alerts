# QBit Alerts

[![License](https://img.shields.io/badge/license-Apache%202.0-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Java](https://img.shields.io/badge/java-21+-blue.svg)](https://adoptium.net/)

> QQQ QBit: alerts and manage-by-exception (monitors, alerts, subscriptions, channels and notifications as standard QQQ tables).

**Status:** design: [QRun-IO/qqq#1090](https://github.com/QRun-IO/qqq/pull/1090). Phases P1 to P3 of the design are implemented: tables, engine, monitors, in-app and email channels, the Alerts app home, and the bell and record-panel API. Not yet published.

## What it does

Any qbit or app reports exceptional conditions ("a person should do something"). The qbit:

- **Records them** as alerts, keyed by `(type, dedupKey)`. Repeats update the open alert; they never duplicate it.
- **Runs the alert lifecycle:** acknowledge, snooze, assign, comment and resolve, with a reopen window, flapping detection and auto-resolve (by the producer, the monitor, or a quiet period).
- **Evaluates monitors**, which are plain data an admin can edit:
  - `TABLE_QUERY` and `AGGREGATE`
  - `HEARTBEAT`, on a key, a process or a table's newest row
  - `PROCESS_FAILED`
  - `CODE`, through `AlertCheckInterface`
- **Notifies subscribers** (users, teams or channels) through in-app (the bell), email and email lists. These optional channels are also built in:
  - Slack (an Incoming Webhook)
  - Microsoft Teams (a Workflows webhook)
  - a generic signed webhook
  - PagerDuty (Events API v2)
  - Prometheus Alertmanager, as a sink

  Any channel another qbit registers works too. Every suppressed notification is logged with its reason.
- **Ingests Prometheus Alertmanager** at `POST /qqq/hooks/alerts/alertmanager`, with notifications off for those types, so infrastructure alerts are triaged in one place.
- **Owns the Alerts home:** open alerts by severity, my alerts, needs action, by group, a 30-day trend, the noisiest types, delivery health and recently resolved. Other pages get link-only summaries from `AlertSummaryLoader`.

All data is in standard QQQ tables named `alerts_*`. People change alerts through record actions, never by editing rows.

## User notifications

The qbit also carries ordinary notifications for people (design: [docs/NOTIFICATIONS.md](docs/NOTIFICATIONS.md)):

- **Admins** with `alerts.notification.admin` write notifications in the Notifications table (title, markdown body, severity, category, link, audience, publish and expire times, a sticky banner flag), then use the record actions Preview audience, Send now, Schedule and Retract. Edits after sending show at once; retracting hides it.
- **Audiences** are resolved when sent: all users, roles or groups, holders of a permission, or named users. Each user gets one `alerts_notification_recipient` row, so read, unread and dismissed are per user. Roles need a user directory (`AlertsQBitConfig.withUserDirectory`, an `AlertsUserDirectoryInterface`); without one, "all users" means everyone who has used Alerts.
- **Producers** send with `QAlerts.notify(new NotificationSignal(...))` using `com.kingsrook.qqq.backend.core.alerts` types (RC.6). Alerts a user subscribes to arrive in the bell as ALERT notifications.
- **Ready-made notices** in `AlertsNotices`: `newSignIn`, `apiKeyCreated`, `apiKeyExpiring`, `permissionChanged` (SECURITY), `shared` (SHARE) and `release` (RELEASE, all users). Core has no sign-in or API key events yet, so hosts call these from their authentication customizer and API key screens.
- **Email** is opt-in per user and category (IMMEDIATE, or DAILY_DIGEST at the digest hour in My Alert Settings), through the configured messaging provider; without one it is off. ALERT notifications are not emailed here (alert subscriptions already do), and SECURITY emails carry only the title and link.
- **Failed and partial jobs** notify the person who started them (`notifyFailedJobs`, `jobRunLinkPattern`).
- **Links** must be app paths with one leading slash, or https URLs; backslashes, whitespace and control characters are refused.
- **People** see them in the Next UI bell and notifications page, and choose per-category settings in My Notification Settings (security notices can't be muted).

Grant `getNotificationAdminPermissionNames()` to authors. Viewers' permissions include their own recipient rows and settings.

**Upgrading from 0.1.0-RC.1:** changeset 16 renames the delivery log `alerts_notification` to `alerts_delivery`, and changeset 17 drops `alerts_inbox_item` (bell state only). Both are guarded, so a fresh schema and an RC.1 schema both end up the same.

The UI routes are published as the `alerts` extension (`1.0.0`, established prefix `/alerts`), with
[the OpenAPI fragment](src/main/resources/openapi/qqq-v1/alerts.yaml) and a contract test.
See [UI API and webhook migration](docs/UI-API.md) for operation IDs and the complete Next handoff.
Summary `inboxHref`, item/alert `actions`, and panel `follow.available` are additive. Existing
hrefs, `notificationsHref`, `alertsHref`, `canManage` and deprecated `watching` remain.

Record following belongs to collaboration: `POST /alerts/record-panel/watch` is removed. Old
RECORD subscriptions remain readable without migration. The panel creates no subscriptions;
Next uses its collaboration module when `follow.available` is true. Alerts with active record
refs also reach followers supplied by core's instance-level followers provider (`FollowEvent.ALERT`),
when FOLLOW is enabled and the follower can read the source record. Followers and subscribers
share recipient deduplication, channel preferences and suppression. No collaboration qbit dependency
is required; without a provider, subscription behavior is unchanged. See [follower routing](docs/UI-API.md#record-follower-routing).

The old Alertmanager path is available for one minor release only via
`withLegacyAlertmanagerPath(true)` (default false). It requires non-strict UI route mode,
returns `Deprecation: true` and `Sunset: Tue, 13 Apr 2027 00:00:00 GMT` on every call, and logs
a warning. Both paths use the same bearer-token check. Strict hosts use the canonical hook.

## Requirements

- Java 21.
- QQQ `4.1.0-RC.6` (released on Maven Central).
- An RDBMS backend.
- A scheduler, for monitors and email delivery.
- For email, either [qbit-email](https://github.com/QRun-IO/qbit-email) produced in the same instance, or a messaging provider.
- `qqq-middleware-javalin`, for the `/qqq/v1/alerts` routes (optional).

The build uses `qbit-build-parent:2.1.0` with architecture guardrails and strict UI API route
tests enabled. Run `./mvnw -B verify` (`.\mvnw.cmd -B verify` on Windows), without a snapshot
override. The default-active `qqq-snapshot` profile retains its historical name but selects
the released RC.6 BOM ahead of the parent's QQQ 4.0 BOM.

## Install

```java
new AlertsQBitProducer()
   .withQBitConfig(new AlertsQBitConfig()
      .withBackendName("rdbms")
      .withSchedulerName("quartz")
      .withMessagingProviderName("smtp")
      .withFromAddress("alerts@example.com")
      .withPublicBaseUrl("https://admin.example.com"))
   .produce(qInstance);
```

1. **Run the schema.** Include the bundled Liquibase changelog from the host's master changelog. The qbit never runs DDL.

   ```yaml
   - include: { file: db/liquibase/qbit-alerts/alerts.changelog.sql }
   ```

2. **Add the security keys to sessions.** Call `AlertsSecurity.applyToSession(qInstance, session)` from your authentication customizer, after the user and permissions are set. The qbit's own home, bell and panel endpoints also add the keys when they are missing.

3. **Map roles to permissions.** `AlertsQBitConfig.getViewerPermissionNames()`, `getManagerPermissionNames()` and `getAdminPermissionNames()` list what to grant: `alerts.view`, `alerts.manage` and `alerts.admin`, plus the standard per-table permissions. Nothing is granted by default.

4. **Optional: ESB and Quick Search.** `AlertsIntegrations` names the main tables and searchable fields for the host's ESB and Quick Search configuration.

## Raising alerts

Use the producer SPI from `com.kingsrook.qqq.backend.core.alerts`, released in QQQ 4.1.0-RC.5 ([QRun-IO/qqq#1108](https://github.com/QRun-IO/qqq/pull/1108)). `AlertsQBitProducer` registers the qbit's sink through core's `AlertsInstanceMetaData`, so host producers need only core:

```java
QAlerts.raise(new AlertSignal("cdn.purge.dead")
   .withDedupKey("provider:" + providerId)
   .withTitle("3 CDN purges failed after retries")             // external-safe: no personal data
   .withRecordRefs(AlertRecordRef.of("cdn_purge_request", ids)));
```

`QAlerts.resolve(...)` and `QAlerts.heartbeat(...)` reach the same sink. Declare types with core's `QAlertTypeMetaData`. Calls through `QAlerts.raise/resolve` defer to transaction commit when a transaction is present; without this qbit installed, core uses its logging sink.

User notifications use core `QAlerts.notify(NotificationSignal)` (RC.6, QRun-IO/qqq#1125). Core dispatches through `AlertsSinkAdapter` into the same bounded queue, notification service, audience resolution and email preferences. Calls wait for an explicit or ambient transaction to commit and are dropped on rollback.

**Breaking Java API migration:** the entire `com.kingsrook.qbits.alerts.spi` package and `AlertsRuntime.sink()` are removed. Replace notification and `AlertLink` imports with `com.kingsrook.qqq.backend.core.alerts`, and replace `AlertsRuntime.get(qInstance).sink().notify(signal)` with `QAlerts.notify(signal)` in the producer's `QContext`. Rebuild callers; qbit-easypost-tracking#16 already moved to core. `getSink()` is for adapter delegation and queue lifecycle management, not a producer API. Notification tables and HTTP contracts are unchanged by this migration.

## Endpoints (for the Next UI)

| Method | Path | What |
|---|---|---|
| GET | `/qqq/v1/alerts/inbox/summary` | Unread count, highest unread severity, the latest 10 notifications and sticky banners (the bell) |
| GET | `/qqq/v1/alerts/inbox` | The notifications page: `status` (all, unread), `category`, `severity`, `offset`, `limit` |
| POST | `/qqq/v1/alerts/inbox/read` | `{"id": n}`, `{"ids": [..]}`, `{"all": true}`, or `{"alertId": n}` |
| POST | `/qqq/v1/alerts/inbox/unread` | `{"id": n}` or `{"ids": [..]}` |
| POST | `/qqq/v1/alerts/inbox/dismiss` | `{"id": n}`, `{"ids": [..]}` or `{"all": true}` |
| GET | `/qqq/v1/alerts/record-panel?tableName=t&recordId=id` | The "Open alerts" panel for a record (`visible: false` when there is nothing to show) |
| POST | `/qqq/hooks/alerts/alertmanager` | Alertmanager's webhook receiver (version 4). It takes a bearer token whose reference is set by `withAlertmanagerIngestTokenSecretRef`, and is off without one. |

## External channels

Each external channel is an `alerts_channel` row that an admin creates. Its secret is a reference (`secretRef`, an environment variable name or a `${...}` expression), never the value. The minimum detail level is MINIMAL by default.

| Type | Secret | Settings |
|---|---|---|
| `SLACK` | The Incoming Webhook URL | none |
| `TEAMS` | The Workflows "when a webhook request is received" URL | none |
| `WEBHOOK` | The signing key (`whsec_...`) | `{"url": "https://receiver"}` |
| `PAGERDUTY` | The Events API v2 routing key | optional `url`, `source` |
| `ALERTMANAGER` | An optional bearer token | `{"url": "https://alertmanager:9093"}` |

How each one sends:

- **Webhook** bodies are `{type, subject, severity, link, alerts: [...]}`, signed by core's `WebhookSigner` with the Standard Webhooks headers (`webhook-id`, `webhook-timestamp`, `webhook-signature: v1,<base64 HMAC-SHA256 of id.timestamp.body>`). Both raw UTF-8 and `whsec_` base64 secrets keep the same wire format. Core's `WebhookSecret` requires 16 key bytes; a thin adapter zero-pads legacy shorter keys to 16 bytes, which is HMAC-SHA256-equivalent and preserves their signatures. New secrets should use `WebhookSecret.generate()`.
- **PagerDuty** gets one event per alert: `trigger`, `acknowledge` or `resolve`, with `dedup_key` set to the type and key.
- **Alertmanager** alerts carry the labels `qqq_type`, `qqq_group`, `qqq_key` and `qqq_origin="qbit-alerts"`. Ingest ignores alerts with that origin, so nothing loops.
- **Responses:** 2xx is sent. 408, 425, 429, 5xx and network errors are retried. Any other 4xx fails permanently.
- **URLs** must be https, unless the channel's settings say `"allowInsecureHttp": true`, which is meant for local receivers.

## Email through qbit-email

When qbit-email is produced in the same instance (and on the classpath; it is an optional dependency), alert channel emails, notification emails and daily digests go through the published `com.kingsrook.qbits.email.api.EmailApi` in qbit-email `0.1.0-RC.2` instead of core `SendMessageAction`. Template registration, sends and installation detection use only the public API; templates, categories, idempotency keys, linked records and outcome mapping are unchanged:

- **Templates**, registered on first use and never overwritten (edit them in the Email app): `alerts_alert` (a channel email for one alert or a batch), `alerts_notification` (one notification) and `alerts_digest` (a list of items, `{{#each items}}`). All are Markdown, category NOTIFICATION, so unsubscribe and suppression apply.
- **One recipient per message.** An email list channel sends one message per address.
- **Idempotency keys** never hold an address: `alerts:alert:<alert ids>:<recipient hash>:<message kind>:<minute>` (the kind and minute keep a reminder or a reopened alert from looking like a repeat), `alerts:notification:<notification id>:<recipient hash>` and `alerts:digest:<user>:<digest window start>`.
- **Linked record** is the alert or notification row; the sender is `qbit-alerts` and the process `alertsDeliverNotifications`.
- **Outcomes.** Queued or duplicate is sent. Suppressed (the person unsubscribed) is `SKIPPED` for notifications and a permanent failure that says so for channels. Refused (for example no enabled email provider) is `FAILED` and not retried.
- **Text** in the variables is the existing redacted, external-safe text. No record labels are passed.
- **Config.** `messagingProviderName` and `fromAddress` are not needed (the From address comes from the email provider). Without qbit-email they work as before.

## Reminders and escalation

The hidden `alertsEscalateAndRemind` process runs every minute on `schedulerName`.
Without a configured scheduler, hosts can invoke the process themselves. Apply
Liquibase changesets 23–26 before running this version.

An OPEN, unacknowledged alert sends REMINDER deliveries to matching subscriptions
whose events include REMINDER (an empty events list includes all kinds). Set the
type's `reminderIntervalMinutes`: null defaults to 60 for CRITICAL alerts and off
otherwise; zero disables reminders, including CRITICAL. The interval starts at
the later of the latest raise and the latest successful notification. Delivery
uses the normal permission, channel and suppression checks, writes NOTIFIED in
the timeline when something is queued, and updates `lastNotifiedAt`.

Admins can create **Escalation Policies** in the Alerts app and add **Steps** on a
policy's record screen. Attach `escalationPolicyId` to a type or group; a type's
explicit policy takes precedence, including when that policy is disabled. Steps
have a positive, unique `stepOrder`, a nonnegative `afterMinutes`, and exactly
one USER (`userId`), TEAM (`teamId`) or CHANNEL (`channelId`) target. User and team
targets use the users' normal in-app/email preferences. Steps apply only to OPEN
alerts matching `onlyIfStatus` (default OPEN). Timing starts at the first raise
or the latest reopen; repeated raises do not postpone escalation.

Each due step sends ESCALATED through the normal delivery checks, records an
ESCALATED event with the previous and new levels, and advances `escalationLevel`.
After the final step, positive `repeatAfterMinutes` and `maxRepeats` allow that
many additional attempts at the final target. Null/zero disables repeats. Repeats
leave the level unchanged and are counted in the timeline within the current
open cycle. Reopening resets the level and repeat budget. One step runs per tick;
a delayed scheduler catches up over subsequent minutes instead of sending a burst.
Acknowledgement, snooze, resolution, mute, flapping, and a disabled or silent type
stop timed notifications. Suppressed deliveries still count as escalation attempts.

Each alert's delivery rows, timeline and progress commit together. The persisted
`timedNotificationCheckedAt` prevents duplicate attempts in the same minute,
including when no recipient is eligible. Run this job on **one scheduler node**;
there is no distributed lock between scheduler nodes. No ESCALATION feature
switch exists in the current config; enable or disable individual policies.

`EXPIRED` remains a reserved resolution enum value. The design defines no alert
expiration rule, so it is excluded from selectable resolution values and is never
assigned automatically. The Resolve action continues to record MANUAL.

## Known limits (v1)

- **Claims are best-effort.** Delivery and monitor claims use a lease, not an atomic conditional update. With several scheduler nodes, run the scheduled processes on one node, or accept at-least-once delivery.
- **Email threading headers.** `Message-ID` and `In-Reply-To` need core messaging support for custom headers, so they are not set.
- **Recipient visibility.** It is checked through `createAutomatedSessionForUser`. If a host's authentication module can't build a session with permissions for a user, email goes out only for alerts that have no visibility permission and no subject table. In-app delivery is always filtered by the record locks.
- **Outbound HTTP** blocks plain http, but has no full SSRF guard. Channels are admin-only. Core's `SafeHttpClient` (QRun-IO/qqq#1109) replaces the client when it is released.
- **Alertmanager sink:** open alerts are sent with an `endsAt` a week out, not re-posted.
- **Not built yet:** digests, quiet hours, Micrometer metrics, ROLE and IdP-group team resolvers, mirrored lock columns and a configurable table namespace. These are the rest of design phases P4 to P6.

## Build

```bash
./mvnw verify
```

Always use the Maven wrapper (Maven 3.9.11); Maven 3.10 cannot read the parent POM.

## License

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
