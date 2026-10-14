# Alerts UI API and webhook migration

The qbit targets QQQ **4.1.0-RC.6** (pending release). `AlertsQBitProducer` registers the
`alerts` `MiddlewareSpecExtension`, version `1.0.0`, with the established `/alerts` prefix.
`withRegisterRoutes(false)` opts out of both the extension and webhook registration.
The six JSON specs extend `AbstractJsonEndpointSpec`; existing `AlertsApi` logic continues
to enforce caller permissions, recipient ownership and alert visibility.

The deterministic fragment is `src/main/resources/openapi/qqq-v1/alerts.yaml`. Next should
vendor it. The live `/qqq/v1/openapi.yaml` includes these operations, and `/qqq/v1/version`
discovers the `alerts` extension so clients need not infer installation from an app name.

| Method | Path | operationId |
| --- | --- | --- |
| GET | `/qqq/v1/alerts/inbox/summary` | `alertsInboxSummary` |
| GET | `/qqq/v1/alerts/inbox` | `alertsInbox` |
| POST | `/qqq/v1/alerts/inbox/read` | `alertsInboxRead` |
| POST | `/qqq/v1/alerts/inbox/unread` | `alertsInboxUnread` |
| POST | `/qqq/v1/alerts/inbox/dismiss` | `alertsInboxDismiss` |
| GET | `/qqq/v1/alerts/record-panel` | `alertsRecordPanel` |

All existing query parameters, selector precedence, paging defaults and clamps, status
codes, JSON content-type requirements, and response keys remain. IDs refer to recipient
rows for inbox operations; legacy `alertId` selectors remain accepted. `all:true` applies
only to read/dismiss. Items in a summary omit `body`; page items and banners include it.
Nullable fields remain serialized as JSON null.

## Next handoff: additive payload fields

- Summary adds `inboxHref`, currently `/app/notifications`. Retain and honor the existing
  `notificationsHref` and `alertsHref` fields for older clients.
- Every inbox item (including banners) adds `actions`: `name`, `label`, `operationId`.
  An unread item offers `read` (`alertsInboxRead`); a read item offers `unread`
  (`alertsInboxUnread`); both offer `dismiss` (`alertsInboxDismiss`). Invoke the operation
  from the fragment with `{ "id": item.id }`. Existing item `href` semantics are unchanged.
- Every record-panel alert adds `actions`: `name`, `label`, `processName`, `href`.
  Managers see `acknowledge` for OPEN alerts and `snooze` for panel alerts; viewers get an
  empty list. The href is complete, including the process, alert id and table arguments:
  `/app/alertsAcknowledge?recordsParam=recordIds&recordIds=9&tableName=alerts_alert`.
  Clients follow the provided href without constructing a process or table name. Server
  process authorization still applies when an action executes.
- Panel adds `follow: { "available": boolean }`, true exactly when core supplemental
  `MiddlewareSpecExtensionsMetaData` contains an extension named `collaboration`. No
  collaboration dependency or URL is embedded here. Next uses its collaboration module
  to determine the follow endpoint and current follow state.

The provided Next `main` checkout had no alerts client at implementation time. The reviewed
client was `feature/notification-center:src/lib/api/alerts.ts` and its
`src/components/alerts/*` components. Its current process href and watch assumptions are
covered by the migration above.

## Removed watch operation

`POST /qqq/v1/alerts/record-panel/watch` and `AlertsApi.watchRecord` are removed. No panel
request creates a RECORD subscription. `alerts_subscription` continues to handle alert-type
and scope subscriptions; existing record-scoped watch rows are deprecated and remain readable
(and retain their existing routing behavior). No rows are migrated or deleted. The panel's
`watching` boolean is retained for compatibility and reports only those legacy rows; it must
not be used as collaboration follow state.

## Record follower routing

For every active record ref on an alert, `AlertRouter.destinations` calls RC.6's
`RecordFollowers.get(instance, ref, FollowEvent.ALERT)`. Register a core
`RecordFollowersProviderInterface` through instance `RecordFeaturesMetaData.withFollowersProvider`
and enable `RecordFeature.FOLLOW` for the source table (or by instance default). The provider
selects followers for ALERT; core recommends treating ALERT like CHANGE unless the provider
has a finer preference. The alerts qbit does not depend on qbit-collaboration.

Each follower must have a known automated recipient session and be able to query the source
record through its table permissions and record security locks. Missing records and unknown
or denied access do not add follower destinations. The query uses the source table's configured
primary key. Eligible followers join the existing destination map, deduplicated by user and
channel across subscriptions and record refs. Existing subscription destinations take precedence.

Followers use ordinary in-app/email settings, vacation pause for external delivery, alert
visibility checks, type notification switches, mutes, flapping, channel limits and duplicate
suppression. Quiet-hour scheduling remains unimplemented for all alert recipients, as listed
in the README; follower routing does not introduce a separate delivery policy. With no provider,
FOLLOW disabled, or a provider failure, existing subscription routing is unchanged.

## Alertmanager machine webhook

Canonical operation: **POST `/qqq/hooks/alerts/alertmanager`**. This is a raw machine route
outside the UI API and has no UI operationId. It preserves the bearer token, secret reference,
JSON request and `{raised, resolved, ignored}` response. No QQQ user session is required.

The previous **POST `/qqq/v1/alerts/ingest/alertmanager`** is an opt-in raw alias:

The architecture guardrail's static G3 scan reports this intentional raw UI-prefix route
as an advisory. Runtime strict-mode coverage rejects the alias; only the migration test
starts a standalone Javalin instance with it enabled. Normal application tests remain strict.

```java
new AlertsQBitConfig()
   .withLegacyAlertmanagerPath(true)
   .withAlertmanagerIngestTokenSecretRef("ALERTMANAGER_TOKEN");
```

`legacyAlertmanagerPath` defaults to false. Core `UiApiRouteAuditor` compares every raw v1
route against specs and offers no machine-webhook exemption. Therefore enabling the alias
requires non-strict UI route mode; strict startup rejects it. It is intentionally absent
from the UI fragment. Default hosts and all application-server tests run strict successfully.
The migration-only raw provider is tested separately, including strict-auditor rejection.

Both paths call the same handler and authenticate against the same secret. Every alias call,
including rejected authentication, logs a warning and carries:

```http
Deprecation: true
Sunset: Tue, 13 Apr 2027 00:00:00 GMT
```

The alias is supported for the 0.1 minor line only and scheduled for removal in 0.2; the
published calendar sunset is 13 April 2027. Move receivers before either retirement boundary.
No automatic runtime cutoff is applied by these informational headers.

## Core notification migration (#15 part 2)

Core `QAlerts.notify` now feeds the same inbox service through the registered sink. This
migration changes Java imports and producer entry points only: no methods, paths,
operation IDs, request fields, response fields or schemas change relative to PR #19.
The earlier client changes above still apply when adopting the full stacked branch.
The supplied Next `main` checkout still has no alerts client; the
`feature/notification-center:src/lib/api/alerts.ts` client was checked again for this work.

## Verification

Use the Maven wrapper. The committed BOM version is RC.6; while it is unreleased, verify with
an already installed local core build:

```sh
./mvnw -B verify -Dqqq.snapshot.version=4.1.0-ARCHRC6-SNAPSHOT
```

Regenerate intentionally with `-Dtest=AlertsApiContractTest -Dalerts.openapi.update=true test`
(and the same local version override). Normal tests compare the fragment without rewriting it.
Checkstyle uses `checkstyle/config.xml` and the repository's existing short Apache headers.
