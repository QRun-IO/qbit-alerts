# QBit Alerts

[![License](https://img.shields.io/badge/license-Apache%202.0-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Java](https://img.shields.io/badge/java-21+-blue.svg)](https://adoptium.net/)

> QQQ QBit: alerts and manage-by-exception (monitors, alerts, subscriptions, channels and notifications as standard QQQ tables).

**Status:** design: [QRun-IO/qqq#1090](https://github.com/QRun-IO/qqq/pull/1090); implementation in progress. Nothing is published yet.

## Requirements

- Java 21.
- QQQ 4.1 (built against `4.1.0-RC.4`).

## Install

Not yet published. Once released, add `com.kingsrook.qbits:qbit-alerts` and produce the qbit into your `QInstance`:

```java
new AlertsQBitProducer()
   .withQBitConfig(new AlertsQBitConfig().withBackendName("rdbms"))
   .produce(qInstance);
```

## Naming

Tables are `alerts_<name>` (lowercase snake_case, backend name equal to the table name). Permissions are `alerts.<action>`.

## Adopting in another app

Follow the QQQ [adoption checklist](https://github.com/QRun-IO/qqq/blob/develop/docs/migration/adoption-checklist.adoc) once the first release is published.

## Build

```bash
./mvnw verify
```

Always use the Maven wrapper (Maven 3.9.11); Maven 3.10 cannot read the parent POM.

## License

Apache License 2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
