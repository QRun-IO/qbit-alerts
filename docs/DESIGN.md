# QBit Alerts: design

The design for this qbit is tracked in [QRun-IO/qqq#1090](https://github.com/QRun-IO/qqq/pull/1090). This repository implements it. Phases P1 to P3 are built (see README.md); Quiet hours and subscription digests from P4 are also built; the remaining work is listed in README.md.

Deviations from the design, and why:
- **UI API and record following:** the RC.6 alerts extension, collaboration ownership of follow, and machine webhook migration are specified in [UI-API.md](UI-API.md). Existing RECORD watch subscriptions remain readable without migration.
- **Alerts and notifications use core's RC.6 producer API**, including sink registration in core instance metadata (QRun-IO/qqq#1108 and #1125). `AlertsSinkAdapter` delegates `raise`, `resolve`, `heartbeat` and `notify` to the runtime queue. No local SPI types remain; storage-specific audience and severity conversion belongs to `NotificationService`.
- **Visibility checks 1 to 3 (section 7.2)** are enforced by record security locks on session keys (`alerts_permission`, `alerts_readable_table`), not by a pre-query customizer. QQQ has no pre-query customizer, and locks also apply to counts, exports and the API.
- **Heartbeats** are stored in an extra table, `alerts_heartbeat`.
- **Claims** are leases, not atomic conditional updates (see README.md, Known limits).
- **Email threading headers** wait for core messaging support.
- **The delivery log is `alerts_delivery`** (the design calls it `alerts_notification`), and **`alerts_inbox_item` is retired**: user notifications took those roles (NOTIFICATIONS.md section 2).

User notifications (announcements, job failures, shares, security notices, releases and marketing), the recipient model and the Next UI notification center are designed in [NOTIFICATIONS.md](NOTIFICATIONS.md), with the owner decisions of 2026-10-13.

## Quiet hours and digest delivery (#27, part 1)

- Quiet days identify the start day of the window. Missing/invalid time zones use UTC; equal endpoints disable quiet hours. Pause and quiet hours apply only to personal push channels (email, email lists and other non-IN_APP channels), never to the in-app bell.
- Existing delivery statuses are sufficient: pending `DIGEST` items are excluded from the ordinary worker. Changeset 21 adds window keys, a unique channel/window claim and the claim's last included item id to the standard `alerts_delivery` table, mirrored in its entity and H2 DDL.
- Digest keys extend the documented user/window prefix with cadence and channel. This avoids collisions between hourly and daily subscriptions with the same start, different channels, and general notification digests. Channel-only digest keys use the separate `alerts:channel-digest` prefix and UTC windows; email adds a recipient hash for list channels. The unique claim is atomic even across schedulers; immediate-delivery leases retain their existing behavior.
- Delivery has at-most-once dispatch semantics. A crash between claiming and recording a provider result cannot be resolved safely for every supported channel. Such claims fail after ten minutes and are not automatically resent. Transport errors are FAILED; late items after the frozen cutoff are SUPPRESSED/DUPLICATE. The shared email API also receives the durable key.
- Digests summarize alerts represented by events in their window, rather than resending the complete outstanding-alert inventory on every run. Rendering uses MINIMAL detail, checks recipient visibility again (failing closed for unverifiable protected content), and rechecks pause/quiet hours for push channels only. In-app summaries are snapshots of the content visible at dispatch.
- To bound memory, each message accepts 500 source events; overflow fails the window with an actionable error instead of silently dropping items or sending several messages. Each run scans at most 100 due candidates; result updates use pages of 200. This is an explicit v1 limit.
- There is no feature-switch collection or DIGESTS switch in the current config. Reminders, escalation and team member sources are unchanged.
