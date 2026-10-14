# QBit Alerts: user notifications (design)

**Status:** approved design, built in phases (section 11). It extends the alerts model in
[QRun-IO/qqq#1090](https://github.com/QRun-IO/qqq/pull/1090) (`docs/specs/2026-10-12-qbit-alerts-design.md`); it does not add a parallel system.

## 0. Summary

The Alerts qbit also carries ordinary user notifications: announcements an admin writes, a user's own
failed jobs, alerts they subscribe to, things shared or assigned to them, security notices, and release or
marketing messages. Every notification is a row in a standard QQQ table, and every person it reaches gets
one recipient row. The Next UI bell, which today shows only alerts, becomes a notification center: an
unread badge, a popover, mark read, dismiss, a "View all" page with filters, and a banner for sticky
critical items. Admins create, edit, schedule, send, retract and delete notifications in the admin
through standard record screens and record actions.

**Owner requirement (verbatim, 2026-10-13):** "the alerts qbit should work for normal notifications etc
too. and if installed and working, the next UI should support it via the 'bell' in the top nav. it should
show you notifications, allow you to mark them as read etc. in the admin system, the system admins should
be able to create notifications, edit, remove, etc. we should be able to send notifications to all users in
a smart way, or just some etc. and a users own alerts or errors or warnings should work through that
system as well"

## 1. Owner decisions (2026-10-13)

1. **Channels.** In-app (the bell and the notifications page) always. Email is opt-in per user, by
   severity or as a daily digest. Slack stays a group or monitor channel only, never per user.
2. **Audience.** Dynamic audiences: all users, groups or roles, holders of a permission, or named users.
   The audience is resolved at send time and materialized as one recipient row per user, so read,
   unread and dismissed are tracked per user.
3. **Automatic sources into a user's bell:**
   - their failed or partial jobs (processes, ETL, imports and exports they started), linked to the run;
   - alerts they subscribe to;
   - things assigned or shared to them (a notebook share, a record lock, a pending approval);
   - security notices (new sign-in, API key created or expiring, permission change);
   - application releases and marketing notifications (authored by admins or emitted by apps, such as
     "new version released" or a marketing-qbit campaign), sent to an audience.

**Owner decisions (2026-10-12): all recommendations accepted.** Every open question below (section 12)
is decided as recommended.

## 2. How it fits the alerts model

| Before (0.1.0-RC.1) | After |
|---|---|
| `alerts_notification` = the delivery log (one row per send attempt per channel) | Renamed `alerts_delivery` ("Delivery Log"). Same columns, same role. |
| `alerts_inbox_item` = one alert in one user's bell | Retired. In-app alert delivery writes an `alerts_notification` (category ALERT, linked to the alert) and a recipient row. |
| The bell shows alerts only | The bell shows every notification category, alerts included. |
| `alerts_user_preference` (quiet hours, digest hour, time zone) | Kept. Notification email settings per category live in `alerts_notification_preference`, and reuse its time zone and digest hour. |
| Producer SPI: `QAlerts.raise/resolve/heartbeat` | Plus `QAlerts.notify(NotificationSignal)` (section 6). |

The rename and the retirement break the changelog's additive-only rule once, before GA. Nobody runs
0.1.0-RC.1 in production. Changeset 16 renames the table, its key and its indexes, and changeset 17 drops
`alerts_inbox_item` (it holds only bell state). Both are guarded by preconditions, so they are safe on a
fresh schema and on an RC.1 schema.

## 3. Tables

All are standard QQQ tables with record screens, the API, audit and ESB events, secured by permissions
and record locks, never hidden.

### 3.1 `alerts_notification` (Notifications)

| Field | Type | Notes |
|---|---|---|
| `title` | string(200), required | Plain text |
| `body` | text | Markdown, rendered safely (no raw HTML) |
| `severity` | INFO / SUCCESS / WARNING / ERROR / CRITICAL | Default INFO |
| `category` | SYSTEM / JOB / SECURITY / RELEASE / MARKETING / SHARE / ALERT | Default SYSTEM |
| `sourceName` | string | Who emitted it: `admin`, `qbit-alerts`, a qbit or app name |
| `sourceKey` | string | Dedup key from producers (for example `process:<uuid>`). A second notify with the same key and audience updates the first. |
| `sourceTableName`, `sourceRecordId` | string | The record it is about (an alert, a run, a share) |
| `linkUrl`, `linkLabel` | string | A deep link (a record, a table filter, a run, or an https URL) |
| `audienceType` | ALL_USERS / ROLES / PERMISSION / USERS | |
| `audienceValue` | text | Comma-separated role names, one permission, or user ids |
| `publishAt`, `expireAt` | timestamp | Not shown before publishAt; hidden and purged after expireAt |
| `sticky` | boolean | A top banner until the user dismisses it (meant for CRITICAL) |
| `status` | DRAFT / SCHEDULED / SENT / RETRACTED | Default DRAFT |
| `authorUserId` | string | Set on insert |
| `sentAt`, `retractedAt`, `recipientCount`, `readCount` | | Kept by the qbit |

Record screen: identity (title, severity, category, status), Message (body, link), Audience, Schedule,
the Recipients child list, and System.

Edits after sending change what recipients see (the bell reads the notification). Retracting hides it
everywhere. Deleting it deletes its recipient rows.

### 3.2 `alerts_notification_recipient` (Notification Recipients)

A child of the notification: one row per user, unique (`notificationId`, `userId`).

`notificationId`, `userId`, `deliveredAt`, `readAt`, `dismissedAt`, `emailedAt`, `emailStatus`
(NONE / PENDING / SENT / DIGESTED / SKIPPED / FAILED).

Users see and change only their own rows (owner lock on `userId`). Only `readAt` and `dismissedAt` are
editable; inserts and deletes are the qbit's.

### 3.3 `alerts_notification_preference` (My Notification Settings)

One row per user and category, unique (`userId`, `category`); a missing row means the defaults.

`userId`, `category`, `inAppMuted` (boolean; refused for SECURITY), `emailMode` (OFF / IMMEDIATE /
DAILY_DIGEST, default OFF), `emailMinSeverity` (default WARNING). It is owner-locked on `userId`. The
digest hour and time zone come from `alerts_user_preference`.

## 4. Audiences

`AlertsAudienceResolver` resolves an audience to user ids at send time:

| Audience | Resolved to |
|---|---|
| ALL_USERS | Every active user in the directory |
| ROLES | Directory users with any of the named roles or groups |
| PERMISSION | Directory users whose session holds the permission (`createAutomatedSessionForUser`) |
| USERS | The named user ids, as given |

**The user directory** is an SPI, `AlertsUserDirectoryInterface` (`listUsers()` returning id, label,
email and roles), set with `AlertsQBitConfig.withUserDirectory(QCodeReference)`. Without one, the directory
is every user who has used Alerts (a row in `alerts_user_preference`, created the first time a user opens
the bell). That is "all users who sign in", with no host code.

Resolution is capped by `maxAudienceSize` (default 50,000). Recipient rows are inserted in batches of 500.

## 5. Admin

The Notifications table is a main nav entry in the Alerts app (it needs `alerts.notification.admin`).
Admins use the standard create, edit and delete screens. Record actions are streamed-ETL processes with the
standard Review and Result summary lines, never nav entries:

| Action | On | Review, then Result |
|---|---|---|
| **Preview audience** | DRAFT or SCHEDULED (one) | "Would reach 42 users", plus up to 10 sample names. Writes nothing. |
| **Send now** | DRAFT or SCHEDULED (bulk) | "Will send 2 notifications to 57 recipients (1 already sent, skipped)", then "Sent" |
| **Schedule** | DRAFT (bulk), needing a future `publishAt` | Sets SCHEDULED. The delivery job sends it when `publishAt` passes. |
| **Retract** | SENT (bulk) | Sets RETRACTED, which hides it from every bell |

The Alerts home gets one link-only line, "Notifications: 3 scheduled, 12 sent this week", for
notification admins. The home stays the only Alerts dashboard.

## 6. Producer SPI

The notification API below is planned in [QRun-IO/qqq#1125](https://github.com/QRun-IO/qqq/pull/1125)
for 4.1.0-RC.6. The baseline is 4.1.0-RC.5, which ships the core alert SPI
(`com.kingsrook.qqq.backend.core.alerts`) but does **not** yet provide `QAlerts.notify`:

```java
QAlerts.notify(new NotificationSignal("Your export failed")
   .withBody("**Orders export** stopped after 1,200 rows: the file server refused the upload.")
   .withSeverity(NotificationSeverity.ERROR)
   .withCategory(NotificationCategory.JOB)
   .withSourceKey("process:" + processUUID)
   .withLink(AlertLink.toUrl("/app/orders", "Orders"))
   .withAudience(NotificationAudience.user(userId)));
```

- `AlertSinkInterface` gets a default method, `notify(NotificationSignal)`, that logs; existing sinks keep
  working. `LoggingAlertSink` logs at INFO. The qbit's sink queues the signal like an alert signal.
- The guarantees match `raise`: it never throws or blocks, it waits for the commit when a transaction is
  open, and bad input is counted.
- `NotificationAudience`: `allUsers()`, `roles(...)`, `permission(p)`, `users(...)`, `user(id)`.
- Until RC.6 ships, the qbit keeps `spi.NotificationSignal`, `NotificationAudience`,
  `NotificationCategory`, `NotificationSeverity` and `AlertLink`, and hosts call
  `AlertsRuntime.get(qInstance).sink().notify(...)`. Their binary signatures remain unchanged for
  released qbit-easypost-tracking 0.1.x. The return type of `sink()` remains `QbitAlertsSink`;
  there is no local sink interface. This direct notification path queues immediately; the
  transaction-aware `QAlerts.notify` guarantees above describe the planned core API.

### 6.1 Automatic sources

| Source | How | Category |
|---|---|---|
| Failed or partial jobs | The qbit's process lifecycle listener: a run that fails, or completes with error summary lines, notifies the user who started it (the session user; system runs are skipped). The link is the run's table, or `jobRunLinkPattern` with `{processName}` and `{processUUID}`. Turn it off with `notifyFailedJobs(false)`. | JOB |
| Alerts they subscribe to | The router's in-app channel writes an ALERT notification per alert (sourceKey `alert:<id>`) and a recipient row per routed user. A repeat or reopen marks it unread again. | ALERT |
| Shares, record locks, approvals | Producers call `QAlerts.notify(... user(id))`. The first wiring is the SQL Workbench notebook share in kof22. | SHARE |
| Security notices | `AlertsSecurityNotices` (new sign-in, API key created, API key expiring, permission change). Core has no authentication events today, so hosts call it from their authentication customizer. A core hook is follow-up work (section 12, Q5). Security is never mutable in-app. | SECURITY |
| Releases and marketing | Admin-authored notifications, or producers with an audience | RELEASE, MARKETING |

## 7. Delivery

- **In-app:** recipient rows. The bell shows rows that are not dismissed, of SENT notifications whose
  `publishAt` has passed and whose `expireAt` has not.
- **Email** (phase e): through the QQQ messaging provider when configured; otherwise inert. IMMEDIATE
  sends from the delivery job. DAILY_DIGEST sends one email per user at their digest hour, listing their
  unread notifications since the last digest. A muted category is skipped. Email never includes a body
  for SECURITY beyond the title and a link.
- **Slack** stays a channel for alert groups and monitors only.

## 8. Next UI bell and page

Gating is unchanged from #85: no bell and zero requests unless `metaData.apps.alerts` is present.

- **Badge:** the unread count (99+), red when an unread item is CRITICAL. The poller is tiny and lives in the
  main bundle. It polls every `pollSeconds` (60), backs off to 5 minutes on errors, and pauses while the tab
  is hidden. QQQ has no SSE today.
- **Popover** (lazy chunk): newest first, a severity icon, the title, the source, a relative time, and a
  link. You can mark one read or unread, or dismiss it, and the footer has "Mark all read" and "View all".
- **Page** `/app/notifications` (lazy route): filters (All or Unread, category, severity), paging, a markdown
  body, and the same actions.
- **Banner:** sticky, undismissed, unexpired items show above the page content until dismissed.
- **Budget:** total JS 1100 KB. The badge adds well under 1 KB to first load; the popover and the page are
  lazy chunks with their own `lazyChunks` budget entry.

### 8.1 API (`/qqq/v1/alerts`, session-authenticated, `alerts.view`)

| Method | Path | Body or query | Returns |
|---|---|---|---|
| GET | `/inbox/summary` | | `{unreadCount, highestUnreadSeverity, items[≤10], banners[], pollSeconds, notificationsHref}` |
| GET | `/inbox` | `?status=all\|unread&category=&severity=&offset=0&limit=25` | `{items[], total, offset, limit}` |
| POST | `/inbox/read` | `{id}` or `{ids:[..]}` or `{all:true}` (`{alertId}` still works) | `{updated}` |
| POST | `/inbox/unread` | `{id}` | `{updated}` |
| POST | `/inbox/dismiss` | `{id}` or `{ids:[..]}` or `{all:true}` | `{updated}` |

An item is `{id, notificationId, title, body, severity, category, source, href, linkLabel,
deliveredAt, age, unread, sticky}`. `id` is the recipient row id. `body` appears only in `/inbox` and in
banners. `highestUnreadSeverity` is one of the five severities. Every call works in the caller's
session, so the record locks apply.

## 9. Security

- `alerts.notification.admin`: author, send, schedule, retract and delete notifications, and read every
  recipient row (the `alerts_user` all-access key).
- `alerts.view`: the bell, the page, their own recipient rows (read and edit) and their own notification
  settings.
- Recipient and preference rows are owner-locked on `userId`. Notification bodies reach a user only
  through their own recipient rows.
- Markdown is rendered without raw HTML. Links must be relative app paths or https.

## 10. Retention

`expireAt` hides a notification at once. The delivery job's purge step deletes notifications (and their
recipients) whose `expireAt` is more than 7 days past, plus SENT or RETRACTED notifications older than
`notificationRetentionDays` (default 180).

## 11. Phases

| Phase | Scope |
|---|---|
| a | Tables, the rename and retirement, admin screens and actions, audiences and recipients, the bell API, ALERT notifications |
| b | Next UI: badge, popover, page, banner |
| c | Producer SPI (core PR plus the stand-in), job failures |
| d | Security notices, shares, release and marketing producers |
| e | Email: immediate and daily digest |

## 12. Questions, with the recommendations the owner accepted

1. Rename the delivery log rather than pick another name? **Yes**, before GA.
2. Copy notification content per recipient? **No**: recipients read the notification, so edits and
   retractions apply at once.
3. Users added after a send? **They don't get it.** Resolution happens at send time, per decision 2.
4. Default email? **Off** for every category until the user opts in.
5. Core authentication events? **Later.** Hosts call `AlertsSecurityNotices` from their authentication
   customizer until core offers a sign-in event.
6. Server-sent events? **Later.** Polling with backoff is enough at 60 s.
