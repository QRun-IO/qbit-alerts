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
- **Notifies subscribers** (users, teams or channels) through in-app (the bell), email and email lists, and through any channel another qbit registers. Every suppressed notification is logged with its reason.
- **Owns the Alerts home:** open alerts by severity, my alerts, needs action, by group, a 30-day trend, the noisiest types, delivery health and recently resolved. Other pages get link-only summaries from `AlertSummaryLoader`.

All data is in standard QQQ tables named `alerts_*`. People change alerts through record actions, never by editing rows.

## Requirements

- Java 21.
- QQQ 4.1 (built against `4.1.0-RC.4`).
- An RDBMS backend.
- A scheduler, for monitors and email delivery.
- A messaging provider, for email.
- `qqq-middleware-javalin`, for the `/qqq/v1/alerts` routes (optional).

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

Until the core producer SPI ([QRun-IO/qqq#1108](https://github.com/QRun-IO/qqq/pull/1108)) is in a released QQQ RC, the SPI types are a stand-in in `com.kingsrook.qbits.alerts.spi` (see its `package-info.java`). In the meantime, call the qbit's sink directly:

```java
AlertsRuntime.get().sink().raise(new AlertSignal("cdn.purge.dead")
   .withDedupKey("provider:" + providerId)
   .withTitle("3 CDN purges failed after retries")             // external-safe: no personal data
   .withRecordRefs(AlertRecordRef.of("cdn_purge_request", ids)));
```

Once the RC ships, producers call `QAlerts.raise(...)` from core and need no dependency on this qbit.

## Endpoints (for the Next UI)

| Method | Path | What |
|---|---|---|
| GET | `/qqq/v1/alerts/inbox/summary` | Unread count, highest unread severity and the latest 10 items (the bell) |
| POST | `/qqq/v1/alerts/inbox/read` | `{"alertId": n}` or `{"all": true}` |
| GET | `/qqq/v1/alerts/record-panel?tableName=t&recordId=id` | The "Open alerts" panel for a record (`visible: false` when there is nothing to show) |
| POST | `/qqq/v1/alerts/record-panel/watch` | `{"tableName": t, "recordId": id}`: a RECORD subscription for the user |

## Known limits (v1)

- **Claims are best-effort.** Delivery and monitor claims use a lease, not an atomic conditional update. With several scheduler nodes, run the two scheduled processes on one node, or accept at-least-once delivery.
- **Email threading headers.** `Message-ID` and `In-Reply-To` need core messaging support for custom headers, so they are not set.
- **Recipient visibility.** It is checked through `createAutomatedSessionForUser`. If a host's authentication module can't build a session with permissions for a user, email goes out only for alerts that have no visibility permission and no subject table. In-app delivery is always filtered by the record locks.
- **Not in v1:** digests, quiet hours, reminders, escalation, external channels (Slack, Teams, webhook, PagerDuty, Alertmanager), mirrored lock columns and a configurable table namespace. These are design phases P4 to P6.

## Build

```bash
./mvnw verify
```

Always use the Maven wrapper (Maven 3.9.11); Maven 3.10 cannot read the parent POM.

## License

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
