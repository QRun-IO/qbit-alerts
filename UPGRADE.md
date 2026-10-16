# Upgrading

## Unreleased: quieter notifications

1. **Run the changelog as usual.** Changeset 55 adds the nullable `alerts_type.reopen_window_minutes` column (guarded, additive).
2. **Behavior changes to know about.**
   - A resolve no longer marks the alert's bell entry unread; the entry is updated in place ("Resolved: ..."). A subscription whose events name RESOLVED and differ from the default set keeps the old behavior. Email, Slack and webhook resolve notices are unchanged.
   - "Alert signals dropped" (`alerts.sink.overflow`) now resolves itself once the queue has stayed below half full for a minute. An installed type row keeps its stored Resolve Mode label; the behavior does not depend on it.
   - `AlertsNotices.apiKeyCreated` is removed. Delete the call; there is no replacement notice.
   - The reopen window is now per alert type: an alert type's **Reopen Window Minutes**, else `AlertsQBitConfig.withReopenWindowMinutesByTypePrefix` (default `health.` = 360 minutes), else `withReopenWindowMinutes` (default 30). A health check that blips again within 6 hours reopens the same alert and bell entry instead of creating a new one.

## Unreleased: starter defaults, setup card, table help

1. **Add the defaults changelog** after `alerts.changelog.sql` in the host's master changelog:

   ```yaml
   - include: { file: db/liquibase/qbit-alerts/alerts-defaults.changelog.sql }
   ```

   It is insert-only and guarded (row exists by name: no change; table missing: skipped), so it is safe on an installed host. It creates the `in-app` and `email` channels and the `alerts` and `other` groups only, the same rows the runtime creates on first use.
2. **Behavior changes to know about.**
   - A group's **Owner Team** is now used: an alert at WARNING or above that no subscription covers (by scope and severity) is sent to that team's members; follow-ups (resolved, reminders) go only to members who were told about the raise. Hosts that never set an owner team see no change.
   - **Default Channels** on My Alert Settings is now used for subscriptions that name no channels. The bell stays whenever In-App is not turned off; the list replaces the built-in email choice with the channels you chose (unknown ids are ignored). Hosts that never set it see no change.
   - Alert types, groups and default monitors now also sync on the first request of the bell, not only on the home or the first signal.
3. Nothing else is required. The help bundle and the Setup card need no configuration.
