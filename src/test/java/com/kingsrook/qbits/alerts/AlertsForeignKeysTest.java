/*
 * Copyright 2026 Kingsrook, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kingsrook.qbits.alerts;


import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import com.kingsrook.qbits.alerts.metadata.AlertsTableMetaDataHelper;
import com.kingsrook.qqq.backend.core.instances.QInstanceEnricher;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.joins.QJoinMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.DirectoryResourceAccessor;
import liquibase.resource.ResourceAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** Referential integrity guardrail: the shipped changelog, applied by Liquibase
 ** to PostgreSQL, gives every relation the metadata declares (one-to-many joins
 ** and table possible-value fields) a foreign key with the decided ON DELETE
 ** rule, led by an index; old orphan rows are quarantined, not lost; and a host
 ** that already added a constraint under the same name skips it.
 *******************************************************************************/
@ExtendWith(RequiresDockerCondition.class)
@Testcontainers
class AlertsForeignKeysTest
{
   private static final String CHANGELOG = "db/liquibase/qbit-alerts/alerts.changelog.sql";

   /////////////////////////////////////////////////////////////////////////////////////
   // child_table.child_column -> parent_table and ON DELETE rule (pg_constraint       //
   // confdeltype: c CASCADE, n SET NULL, r RESTRICT). Add a row here with the new     //
   // changeset when a table gains a relation; reasons are in the changeset comments. //
   /////////////////////////////////////////////////////////////////////////////////////
   private static final Map<String, String> EXPECTED = new TreeMap<>(Map.ofEntries(
      Map.entry("alerts_type.group_id", "alerts_group n"),
      Map.entry("alerts_type.escalation_policy_id", "alerts_escalation_policy n"),
      Map.entry("alerts_group.parent_group_id", "alerts_group n"),
      Map.entry("alerts_group.owner_team_id", "alerts_team n"),
      Map.entry("alerts_group.escalation_policy_id", "alerts_escalation_policy n"),
      Map.entry("alerts_monitor.alert_type_id", "alerts_type r"),
      Map.entry("alerts_alert.alert_type_id", "alerts_type r"),
      Map.entry("alerts_alert.monitor_id", "alerts_monitor n"),
      Map.entry("alerts_alert.group_id", "alerts_group n"),
      Map.entry("alerts_alert.assigned_team_id", "alerts_team n"),
      Map.entry("alerts_record_ref.alert_id", "alerts_alert c"),
      Map.entry("alerts_event.alert_id", "alerts_alert c"),
      Map.entry("alerts_delivery.alert_id", "alerts_alert c"),
      Map.entry("alerts_delivery.alert_event_id", "alerts_event n"),
      Map.entry("alerts_delivery.subscription_id", "alerts_subscription n"),
      Map.entry("alerts_delivery.channel_id", "alerts_channel r"),
      Map.entry("alerts_subscription.team_id", "alerts_team r"),
      Map.entry("alerts_subscription.channel_id", "alerts_channel r"),
      Map.entry("alerts_team_member.team_id", "alerts_team c"),
      Map.entry("alerts_notification_recipient.notification_id", "alerts_notification c"),
      Map.entry("alerts_escalation_step.policy_id", "alerts_escalation_policy c"),
      Map.entry("alerts_escalation_step.team_id", "alerts_team r"),
      Map.entry("alerts_escalation_step.channel_id", "alerts_channel r")
   ));

   //////////////////////////////////////////////////////////////////////////////////////
   // declared relations that deliberately have no foreign key, with the reason. None //
   // today: the polymorphic references (alerts_alert.subject_table_name/record_id,   //
   // alerts_record_ref.table_name/record_id, alerts_notification.source_table_name/  //
   // source_record_id, subscription and mute scope_value/scope_record_id) and the    //
   // id lists (channel_override_ids, default_channel_ids) are not declared as joins  //
   // or table possible values, so they never reach this check.                       //
   //////////////////////////////////////////////////////////////////////////////////////
   private static final Map<String, String> EXEMPT = Map.of();

   @Container
   static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

   @TempDir
   Path tempDir;



   /*******************************************************************************
    ** Start every test from an empty schema.
    *******************************************************************************/
   @BeforeEach
   void emptySchema() throws Exception
   {
      try(Connection connection = connect(); Statement statement = connection.createStatement())
      {
         statement.execute("DROP SCHEMA public CASCADE");
         statement.execute("CREATE SCHEMA public");
      }
   }



   /*******************************************************************************
    ** Every declared relation has its foreign key with the decided rule, there
    ** are no undecided ones, and every foreign-key column leads an index.
    *******************************************************************************/
   @Test
   void testEveryRelationHasForeignKey() throws Exception
   {
      update(new ClassLoaderResourceAccessor());
      Map<String, String> actual = foreignKeys();

      for(String relation : declaredRelations().keySet())
      {
         if(EXEMPT.containsKey(relation))
         {
            continue;
         }
         assertThat(actual).as("a foreign key (or an EXEMPT entry with its reason) for " + relation + " -> " + declaredRelations().get(relation)).containsKey(relation);
         assertThat(actual.get(relation)).as("foreign key " + relation + " references " + declaredRelations().get(relation))
            .startsWith(declaredRelations().get(relation) + " ");
      }
      assertThat(actual).as("foreign keys and their ON DELETE rules").isEqualTo(EXPECTED);
      assertThat(unindexedForeignKeys()).as("foreign-key columns no index leads with").isEmpty();
   }



   /*******************************************************************************
    ** Orphans from before the constraints: recorded, then nulled (nullable
    ** column) or deleted (NOT NULL column, full row kept); valid rows untouched.
    ** Running the changelog again changes nothing.
    *******************************************************************************/
   @Test
   void testOrphansAreQuarantined() throws Exception
   {
      update(preForeignKeyChangelog());
      try(Connection connection = connect(); Statement statement = connection.createStatement())
      {
         statement.execute("INSERT INTO alerts_team (id, name, label) VALUES (1, 'ops', 'Ops')");
         statement.execute("INSERT INTO alerts_team_member (id, team_id, user_id) VALUES (1, 1, 'kept'), (2, 999, 'orphan')");
         statement.execute("INSERT INTO alerts_type (id, name, group_id) VALUES (1, 'fk.test', 999)");
         statement.execute("INSERT INTO alerts_monitor (id, name, alert_type_id, kind) VALUES (1, 'orphan monitor', 999, 'TABLE')");
         statement.execute("INSERT INTO alerts_alert (id, alert_type_id, monitor_id, group_id, assigned_team_id) VALUES (1, 1, 1, 999, 1)");
         statement.execute("INSERT INTO alerts_event (id, alert_id, event_type) VALUES (1, 1, 'RAISED'), (2, 999, 'RAISED')");
         statement.execute("INSERT INTO alerts_delivery (id, alert_id, alert_event_id, channel_id) VALUES (1, 1, 2, 999)");
         statement.execute("INSERT INTO alerts_notification_recipient (id, notification_id, user_id) VALUES (1, 999, 'u')");
         statement.execute("INSERT INTO alerts_escalation_policy (id, name) VALUES (1, 'p')");
         statement.execute("INSERT INTO alerts_escalation_step (id, policy_id, step_order, after_minutes, target_type, team_id) VALUES (1, 1, 1, 0, 'TEAM', 999), (2, 1, 2, 5, 'TEAM', 1)");
      }

      update(new ClassLoaderResourceAccessor());
      Set<String> expected = Set.of(
         "alerts_type.group_id 1 999 NULLED",
         "alerts_monitor.alert_type_id 1 999 DELETED",
         "alerts_alert.monitor_id 1 1 NULLED",
         "alerts_alert.group_id 1 999 NULLED",
         "alerts_event.alert_id 2 999 DELETED",
         "alerts_delivery.alert_event_id 1 2 NULLED",
         "alerts_delivery.channel_id 1 999 NULLED",
         "alerts_team_member.team_id 2 999 DELETED",
         "alerts_notification_recipient.notification_id 1 999 DELETED",
         "alerts_escalation_step.team_id 1 999 DELETED");
      assertThat(quarantined()).isEqualTo(expected);

      try(Connection connection = connect(); Statement statement = connection.createStatement())
      {
         assertThat(queryRow(statement, "SELECT alert_type_id, monitor_id, group_id, assigned_team_id FROM alerts_alert WHERE id = 1")).containsExactly("1", null, null, "1");
         assertThat(queryRow(statement, "SELECT alert_id, alert_event_id, channel_id FROM alerts_delivery WHERE id = 1")).containsExactly("1", null, null);
         assertThat(queryRow(statement, "SELECT COUNT(*) FROM alerts_team_member WHERE user_id = 'kept'")).containsExactly("1");
         assertThat(queryRow(statement, "SELECT COUNT(*) FROM alerts_escalation_step WHERE id = 2")).containsExactly("1");
         assertThat(queryRow(statement, "SELECT row_data FROM qqq_fk_quarantine WHERE table_name = 'alerts_monitor'").get(0)).contains("\"orphan monitor\"");
      }

      update(new ClassLoaderResourceAccessor());
      assertThat(quarantined()).as("a second run quarantines nothing more").isEqualTo(expected);
      assertThat(foreignKeys()).isEqualTo(EXPECTED);
   }



   /*******************************************************************************
    ** A host that added a constraint itself (same name) marks that changeset
    ** ran instead of failing on a duplicate constraint.
    *******************************************************************************/
   @Test
   void testExistingConstraintIsSkipped() throws Exception
   {
      update(preForeignKeyChangelog());
      try(Connection connection = connect(); Statement statement = connection.createStatement())
      {
         statement.execute("ALTER TABLE alerts_event ADD CONSTRAINT fk_alerts_event_alert_id FOREIGN KEY (alert_id) REFERENCES alerts_alert (id) ON DELETE CASCADE");
      }
      update(new ClassLoaderResourceAccessor());
      assertThat(foreignKeys()).isEqualTo(EXPECTED);
   }



   /*******************************************************************************
    ** The rules at work: an alert takes its timeline, records and delivery log
    ** with it; a used channel and the type of a monitor cannot be deleted; a
    ** deleted team unassigns its alerts and takes its members.
    *******************************************************************************/
   @Test
   void testOnDeleteRules() throws Exception
   {
      update(new ClassLoaderResourceAccessor());
      try(Connection connection = connect(); Statement statement = connection.createStatement())
      {
         statement.execute("INSERT INTO alerts_type (id, name) VALUES (1, 't')");
         statement.execute("INSERT INTO alerts_monitor (id, name, alert_type_id, kind) VALUES (1, 'm', 1, 'TABLE')");
         statement.execute("INSERT INTO alerts_team (id, name, label) VALUES (1, 'ops', 'Ops')");
         statement.execute("INSERT INTO alerts_team_member (team_id, user_id) VALUES (1, 'u')");
         statement.execute("INSERT INTO alerts_channel (id, name, label, channel_type) VALUES (1, 'c', 'C', 'EMAIL')");
         statement.execute("INSERT INTO alerts_alert (id, alert_type_id, monitor_id, assigned_team_id) VALUES (1, 1, 1, 1)");
         statement.execute("INSERT INTO alerts_event (id, alert_id) VALUES (1, 1)");
         statement.execute("INSERT INTO alerts_record_ref (alert_id) VALUES (1)");
         statement.execute("INSERT INTO alerts_delivery (alert_id, alert_event_id, channel_id) VALUES (1, 1, 1)");

         assertThatThrownBy(() -> statement.execute("DELETE FROM alerts_channel WHERE id = 1")).isInstanceOf(SQLException.class);
         assertThatThrownBy(() -> statement.execute("DELETE FROM alerts_type WHERE id = 1")).isInstanceOf(SQLException.class);

         statement.execute("DELETE FROM alerts_team WHERE id = 1");
         assertThat(queryRow(statement, "SELECT assigned_team_id FROM alerts_alert WHERE id = 1")).containsExactly((String) null);
         assertThat(queryRow(statement, "SELECT COUNT(*) FROM alerts_team_member")).containsExactly("0");

         statement.execute("DELETE FROM alerts_alert WHERE id = 1");
         assertThat(queryRow(statement, "SELECT (SELECT COUNT(*) FROM alerts_event) + (SELECT COUNT(*) FROM alerts_record_ref) + (SELECT COUNT(*) FROM alerts_delivery)")).containsExactly("0");
         statement.execute("DELETE FROM alerts_channel WHERE id = 1");
      }
   }



   /*******************************************************************************
    ** child_table.child_column -> parent_table for every one-to-many join and
    ** every field whose possible values are an alerts table.
    *******************************************************************************/
   private static Map<String, String> declaredRelations() throws Exception
   {
      Map<String, String> relations = new TreeMap<>();
      Set<String>         tables    = new TreeSet<>(AlertsQBitConfig.allTableNames());
      for(QTableMetaData table : AlertsTableMetaDataHelper.buildAllTables(new AlertsQBitConfig()))
      {
         for(QFieldMetaData field : table.getFields().values())
         {
            if(field.getPossibleValueSourceName() != null && tables.contains(field.getPossibleValueSourceName()))
            {
               relations.put(table.getName() + "." + QInstanceEnricher.inferBackendName(field.getName()), field.getPossibleValueSourceName());
            }
         }
      }
      for(QJoinMetaData join : AlertsTableMetaDataHelper.buildJoins())
      {
         relations.put(join.getRightTable() + "." + QInstanceEnricher.inferBackendName(join.getJoinOns().get(0).getRightField()), join.getLeftTable());
      }
      return (relations);
   }



   /*******************************************************************************
    ** The changelog up to (not including) the foreign-key changesets, at the
    ** same path, so the full changelog later continues where it stopped.
    *******************************************************************************/
   private ResourceAccessor preForeignKeyChangelog() throws Exception
   {
      String changelog;
      try(InputStream in = getClass().getClassLoader().getResourceAsStream(CHANGELOG))
      {
         changelog = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }
      Path file = tempDir.resolve(CHANGELOG);
      Files.createDirectories(file.getParent());
      Files.writeString(file, changelog.substring(0, changelog.indexOf("-- changeset qbit-alerts:31")));
      return (new DirectoryResourceAccessor(tempDir));
   }



   /*******************************************************************************
    ** Run Liquibase update with the changelog from the given accessor.
    *******************************************************************************/
   private static void update(ResourceAccessor accessor) throws Exception
   {
      Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connect()));
      try(Liquibase liquibase = new Liquibase(CHANGELOG, accessor, database))
      {
         liquibase.update(new Contexts(), new LabelExpression());
      }
   }



   /*******************************************************************************
    ** child_table.child_column -> "parent_table rule" for every foreign key.
    *******************************************************************************/
   private static Map<String, String> foreignKeys() throws Exception
   {
      Map<String, String> keys = new TreeMap<>();
      try(Connection connection = connect(); Statement statement = connection.createStatement();
         ResultSet result = statement.executeQuery("""
            SELECT c.conrelid::regclass::text, a.attname, c.confrelid::regclass::text, c.confdeltype
            FROM pg_constraint c JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]
            WHERE c.contype = 'f' AND c.conrelid::regclass::text LIKE 'alerts%'"""))
      {
         while(result.next())
         {
            keys.put(result.getString(1) + "." + result.getString(2), result.getString(3) + " " + result.getString(4));
         }
      }
      return (keys);
   }



   /*******************************************************************************
    ** Foreign keys whose column does not lead any index on the child table.
    *******************************************************************************/
   private static List<String> unindexedForeignKeys() throws Exception
   {
      List<String> missing = new ArrayList<>();
      try(Connection connection = connect(); Statement statement = connection.createStatement();
         ResultSet result = statement.executeQuery("""
            SELECT c.conname FROM pg_constraint c
            WHERE c.contype = 'f' AND NOT EXISTS (SELECT 1 FROM pg_index i WHERE i.indrelid = c.conrelid AND i.indkey[0] = c.conkey[1])"""))
      {
         while(result.next())
         {
            missing.add(result.getString(1));
         }
      }
      return (missing);
   }



   /*******************************************************************************
    ** The quarantine log as "table.column rowId missingParentId action".
    *******************************************************************************/
   private static Set<String> quarantined() throws Exception
   {
      Set<String> rows = new TreeSet<>();
      try(Connection connection = connect(); Statement statement = connection.createStatement();
         ResultSet result = statement.executeQuery("SELECT table_name, column_name, row_id, missing_parent_id, action, changeset FROM qqq_fk_quarantine"))
      {
         while(result.next())
         {
            assertThat(result.getString(6)).startsWith("qbit-alerts:");
            rows.add(result.getString(1) + "." + result.getString(2) + " " + result.getString(3) + " " + result.getString(4) + " " + result.getString(5));
         }
      }
      return (rows);
   }



   /*******************************************************************************
    ** The first row of a query, as strings.
    *******************************************************************************/
   private static List<String> queryRow(Statement statement, String sql) throws SQLException
   {
      try(ResultSet result = statement.executeQuery(sql))
      {
         assertThat(result.next()).as(sql).isTrue();
         List<String> values = new ArrayList<>();
         for(int i = 1; i <= result.getMetaData().getColumnCount(); i++)
         {
            values.add(result.getString(i));
         }
         return (values);
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static Connection connect() throws SQLException
   {
      return (DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
   }
}
