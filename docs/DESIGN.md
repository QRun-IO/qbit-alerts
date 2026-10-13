# QBit Alerts: design

The design for this qbit is tracked in [QRun-IO/qqq#1090](https://github.com/QRun-IO/qqq/pull/1090). This repository implements it. Phases P1 to P3 are built (see README.md); P4 to P6 remain.

Deviations from the design, and why:
- **The core SPI is a stand-in** (`spi/`) until QRun-IO/qqq#1108 is released.
- **Visibility checks 1 to 3 (section 7.2)** are enforced by record security locks on session keys (`alerts_permission`, `alerts_readable_table`), not by a pre-query customizer. QQQ has no pre-query customizer, and locks also apply to counts, exports and the API.
- **Heartbeats** are stored in an extra table, `alerts_heartbeat`.
- **Claims** are leases, not atomic conditional updates (see README.md, Known limits).
- **Email threading headers** wait for core messaging support.

User notifications (announcements, job failures, shares, security notices, releases and marketing), the recipient model and the Next UI notification center are designed in [NOTIFICATIONS.md](NOTIFICATIONS.md), with the owner decisions of 2026-10-13.
