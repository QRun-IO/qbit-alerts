# Upgrading

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
