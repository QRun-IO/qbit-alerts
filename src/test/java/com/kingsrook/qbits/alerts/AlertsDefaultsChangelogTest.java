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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qbits.alerts.model.AlertsPossibleValues.ChannelType;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** The shipped starter defaults (db/liquibase/qbit-alerts/alerts-defaults.
 ** changelog.sql), run through real Liquibase on H2 (PostgreSQL mode) against
 ** the qbit's test schema: the rows appear, a second update runs nothing, an
 ** edited row survives, a host without the tables marks the changesets ran,
 ** and the seeded rows are the ones the runtime would create itself.
 *******************************************************************************/
class AlertsDefaultsChangelogTest extends BaseAlertsTest
{
   static final String CHANGELOG = "db/liquibase/qbit-alerts/alerts-defaults.changelog.sql";

   /** the tables the changelog seeds. */
   static final Set<String> SEEDED_TABLES = Set.of("alerts_channel", "alerts_group");



   /*******************************************************************************
    ** Fresh schema, update twice: same rows; an edit survives.
    *******************************************************************************/
   @Test
   void seedsAFreshSchemaInsertOnly() throws Exception
   {
      try(Connection connection = freshDatabase(true))
      {
         update(connection);
         assertThat(names(connection, "alerts_channel")).containsExactlyInAnyOrder("in-app", "email");
         assertThat(names(connection, "alerts_group")).containsExactlyInAnyOrder("alerts", "other");

         try(Statement statement = connection.createStatement())
         {
            statement.execute("UPDATE alerts_channel SET label = 'Bell', enabled = FALSE WHERE name = 'in-app'");
            statement.execute("DELETE FROM alerts_group WHERE name = 'other'");
         }
         connection.commit();
         update(connection);
         assertThat(scalar(connection, "SELECT label FROM alerts_channel WHERE name = 'in-app'")).isEqualTo("Bell");
         assertThat(scalar(connection, "SELECT COUNT(*) FROM alerts_channel")).isEqualTo("2");
         assertThat(scalar(connection, "SELECT COUNT(*) FROM databasechangelog WHERE exectype = 'EXECUTED'")).isEqualTo("4");
      }
   }



   /*******************************************************************************
    ** A host that already has a row by that name (its own settings) keeps it
    ** untouched, and no second one is added.
    *******************************************************************************/
   @Test
   void keepsAHostsExistingRows() throws Exception
   {
      try(Connection connection = freshDatabase(true); Statement statement = connection.createStatement())
      {
         statement.execute("INSERT INTO alerts_channel (name, label, channel_type, enabled, rate_limit_per_hour) VALUES ('email', 'Ops mail', 'EMAIL', FALSE, 5)");
         update(connection);
         assertThat(scalar(connection, "SELECT label FROM alerts_channel WHERE name = 'email'")).isEqualTo("Ops mail");
         assertThat(scalar(connection, "SELECT rate_limit_per_hour FROM alerts_channel WHERE name = 'email'")).isEqualTo("5");
         assertThat(scalar(connection, "SELECT COUNT(*) FROM alerts_channel WHERE name = 'email'")).isEqualTo("1");
      }
   }



   /*******************************************************************************
    ** A host that has not created the tables yet skips every changeset (marked
    ** ran), rather than failing its deploy.
    *******************************************************************************/
   @Test
   void aMissingTableMarksTheChangesetsRan() throws Exception
   {
      try(Connection connection = freshDatabase(false))
      {
         update(connection);
         assertThat(scalar(connection, "SELECT COUNT(*) FROM databasechangelog WHERE exectype = 'MARK_RAN'")).isEqualTo("4");
      }
   }



   /*******************************************************************************
    ** Every changeset guards itself and uses only precondition forms that parse
    ** in Liquibase 4 and 5.
    *******************************************************************************/
   @Test
   void everyChangesetIsGuarded() throws Exception
   {
      String text = changelogText();
      assertThat(text).doesNotContain("precondition-table-exists");
      String[] changesets = text.split("(?m)^(?=-- changeset )");
      List<String> parts = new ArrayList<>();
      for(String part : changesets)
      {
         if(part.startsWith("-- changeset "))
         {
            parts.add(part);
         }
      }
      assertThat(parts).hasSize(4);
      for(String part : parts)
      {
         assertThat(part).contains("-- preconditions onFail:MARK_RAN onError:MARK_RAN").contains("precondition-sql-check expectedResult:0").contains("-- rollback ").contains("NOT EXISTS");
      }
      for(String table : SEEDED_TABLES)
      {
         assertThat(text).contains("INSERT INTO " + table + " ");
      }
   }



   /*******************************************************************************
    ** The seeded rows equal what the runtime creates by itself when they are
    ** missing, so a deploy never changes behavior.
    *******************************************************************************/
   @Test
   void seededRowsEqualTheRuntimeDefaults() throws Exception
   {
      QRecordPair in    = runtimeChannel(ChannelType.IN_APP);
      QRecordPair email = runtimeChannel(ChannelType.EMAIL);
      Integer     alertsGroup = runtime.asSystem(() -> runtime.getTypeSync().ensureGroup("alerts"));
      Integer     otherGroup  = runtime.asSystem(() -> runtime.getTypeSync().ensureGroup(com.kingsrook.qbits.alerts.engine.AlertTypeSync.DEFAULT_GROUP));

      try(Connection connection = freshDatabase(true))
      {
         update(connection);
         for(QRecordPair channel : List.of(in, email))
         {
            for(Map.Entry<String, String> column : channel.columns.entrySet())
            {
               assertThat(scalar(connection, "SELECT " + column.getKey() + " FROM alerts_channel WHERE name = '" + channel.name + "'")).as(channel.name + " " + column.getKey()).isEqualTo(column.getValue());
            }
         }
         assertThat(scalar(connection, "SELECT label FROM alerts_group WHERE name = 'alerts'")).isEqualTo(groupLabel(alertsGroup));
         assertThat(scalar(connection, "SELECT label FROM alerts_group WHERE name = 'other'")).isEqualTo(groupLabel(otherGroup));
      }
   }



   /*******************************************************************************
    ** The channel the runtime makes when missing, as comparable column values.
    *******************************************************************************/
   private QRecordPair runtimeChannel(ChannelType type) throws Exception
   {
      return (runtime.asSystem(() ->
      {
         var row = runtime.getChannels().defaultChannel(type);
         QRecordPair pair = new QRecordPair(row.getValueString("name"));
         pair.columns.put("label", row.getValueString("label"));
         pair.columns.put("channel_type", row.getValueString("channelType"));
         pair.columns.put("detail_level", row.getValueString("detailLevel"));
         pair.columns.put("min_severity", row.getValueString("minSeverity"));
         pair.columns.put("rate_limit_per_hour", String.valueOf(row.getValueInteger("rateLimitPerHour")));
         pair.columns.put("health_status", row.getValueString("healthStatus"));
         return (pair);
      }));
   }



   /*******************************************************************************
    ** The label of a group the runtime created.
    *******************************************************************************/
   private String groupLabel(Integer id) throws Exception
   {
      return (runtime.asSystem(() -> com.kingsrook.qbits.alerts.engine.AlertsDb.byId("alerts_group", id).getValueString("label")));
   }



   /*******************************************************************************
    ** A name and its expected column values.
    *******************************************************************************/
   private static final class QRecordPair
   {
      private final String                         name;
      private final Map<String, String> columns = new java.util.LinkedHashMap<>();



      /*******************************************************************************
       **
       *******************************************************************************/
      private QRecordPair(String name)
      {
         this.name = name;
      }
   }



   /*******************************************************************************
    ** A fresh in-memory H2 (PostgreSQL mode), with or without the qbit's tables.
    *******************************************************************************/
   static Connection freshDatabase(boolean withTables) throws Exception
   {
      Connection connection = DriverManager.getConnection("jdbc:h2:mem:defaults" + System.nanoTime() + ";MODE=PostgreSQL;DEFAULT_NULL_ORDERING=HIGH");
      if(withTables)
      {
         String script;
         try(InputStream in = AlertsDefaultsChangelogTest.class.getClassLoader().getResourceAsStream("alerts-h2.sql"))
         {
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
         }
         try(Statement statement = connection.createStatement())
         {
            StringBuilder current = new StringBuilder();
            for(String line : script.split("\n"))
            {
               if(line.trim().startsWith("--"))
               {
                  continue;
               }
               current.append(line).append('\n');
               if(line.trim().endsWith(";"))
               {
                  statement.execute(current.toString().trim().replaceAll(";$", ""));
                  current.setLength(0);
               }
            }
         }
      }
      return (connection);
   }



   /*******************************************************************************
    ** Run the changelog with real Liquibase.
    *******************************************************************************/
   static void update(Connection connection) throws Exception
   {
      Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
      //////////////////////////////////////////////////////////////////
      // not closed: closing Liquibase would close the test connection //
      //////////////////////////////////////////////////////////////////
      new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database).update("");
      connection.commit();
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static String changelogText() throws Exception
   {
      try(InputStream stream = AlertsDefaultsChangelogTest.class.getClassLoader().getResourceAsStream(CHANGELOG))
      {
         assertThat(stream).as(CHANGELOG + " is on the classpath").isNotNull();
         return (new String(stream.readAllBytes(), StandardCharsets.UTF_8));
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static List<String> names(Connection connection, String table) throws Exception
   {
      List<String> names = new ArrayList<>();
      try(Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery("SELECT name FROM " + table))
      {
         while(rs.next())
         {
            names.add(rs.getString(1));
         }
      }
      return (names);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String scalar(Connection connection, String sql) throws Exception
   {
      try(Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql))
      {
         rs.next();
         return (rs.getString(1));
      }
   }

}
