# CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

## Project Overview

**QBit Alerts**: QQQ QBit: alerts and manage-by-exception (monitors, alerts, subscriptions, channels and notifications as standard QQQ tables). Design: [QRun-IO/qqq#1090](https://github.com/QRun-IO/qqq/pull/1090). Phases P1 to P3 are implemented; see README.md for the remaining phases and the core SPI stand-in in `spi/`.

## Build Commands

```bash
./mvnw compile          # Compile (always use the wrapper: Maven 3.10 cannot read the parent POM)
./mvnw test             # Unit tests
./mvnw verify           # Unit tests + JaCoCo gate (70% instructions, 90% classes)
```

## Architecture

**Entry point:** `AlertsQBitProducer implements QBitMetaDataProducer<AlertsQBitConfig>` in `com.kingsrook.qbits.alerts`. Component producers are found by package scanning, in the `metadata` package only (the `api` package needs the optional Javalin dependency).

Packages:
- `model`: entities, generated from one schema together with the changelog and the test DDL. If you change a table, change all three.
- `engine`: the state machine (`AlertsEngine`), routing (`AlertRouter`), monitors (`MonitorEvaluator`), type sync, the sink and the process listener.
- `delivery`: channels, rendering, redaction and the delivery worker.
- `processes`: the record actions (`AlertsAction`, `AlertsActionSteps`) and the scheduled steps.
- `home`: the Alerts home and `AlertSummaryLoader`.
- `api`: the bell and record-panel routes.
- `spi`: the stand-in for the core producer SPI.

## Naming (owner rule)

- Tables are lowercase snake_case prefixed with the full qbit name: `alerts_<name>`. The QQQ table name and the backend (SQL) name are identical; possible-value sources take the table name.
- Permissions use the full qbit name: `alerts.<action>` (for example `alerts.view`, `alerts.admin`).
- Qbit data lives in standard, visible QQQ tables, secured by permissions and record security locks, never hidden.
- Processes run as table or record actions, never in the nav.

## Code Style

- 3-space indentation, opening braces on the next line, `return (x);`.
- Javadoc flower-box comments (`/*****` with `**` lines) on every class and method.
- Fluent setters (`withX()`).
- Wrapper types (`Integer`, `Boolean`) in config and entities.

## Dependencies

- Java 21.
- Parent `com.kingsrook:qbit-build-parent:2.0.0` (qqq 4.0.0). The default-active `qqq-snapshot` profile imports `qqq-bom-pom:4.1.0-RC.4` ahead of it, as in qbit-quick-search (ADR-0007). Remove that activation when the parent moves to 4.1.
- Tests: JUnit 5, AssertJ.

## Release

- CircleCI qqq-orb 0.6.8.
- `develop` publishes SNAPSHOTs.
- A `release/*` branch publishes RCs (the orb bumps the version).
- A `v*` tag publishes the release.
