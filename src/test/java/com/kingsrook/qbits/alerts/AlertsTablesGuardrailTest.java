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


import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import com.kingsrook.qbits.alerts.metadata.AlertsHelp;
import com.kingsrook.qqq.backend.core.model.metadata.help.QHelpContent;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Guardrail (owner requirement 2026-10-14: "all tables should be populated
 ** and the qbit should be fully using them"). For every alerts_* table:
 ** - runtime code (not just its model, metadata, config or home screen) reads
 **   or writes it, so no table exists only on paper;
 ** - it is either seeded by the shipped defaults changelog, or listed here
 **   with why it starts empty;
 ** - it has bundle help that says what it is for and how its rows get created.
 ** A new table fails this test until it is wired, seeded or explained.
 *******************************************************************************/
class AlertsTablesGuardrailTest extends BaseAlertsTest
{
   private static final Path MAIN = Path.of("src", "main", "java", "com", "kingsrook", "qbits", "alerts");

   /*******************************************************************************
    ** Tables that legitimately start empty, and where their rows come from.
    *******************************************************************************/
   static final Map<String, String> STARTS_EMPTY = Map.ofEntries(
      Map.entry("alerts_type", "synced from the alert types the installed qbits declare in code, on the first visit (AlertTypeSync)"),
      Map.entry("alerts_monitor", "default monitors are shipped by the declaring qbits and seeded by AlertTypeSync; admins add the rest"),
      Map.entry("alerts_alert", "runtime: a producer or monitor raises an alert"),
      Map.entry("alerts_record_ref", "runtime: written with an alert that names records"),
      Map.entry("alerts_event", "runtime: the alert timeline"),
      Map.entry("alerts_subscription", "people's own choices; the first alerts admin gets one by the bootstrap rule (AlertsPreferences)"),
      Map.entry("alerts_user_preference", "user content: created per person on their first visit (AlertsPreferences)"),
      Map.entry("alerts_team", "the host's own people (kof22 seeds its team)"),
      Map.entry("alerts_team_member", "the host's own people"),
      Map.entry("alerts_mute", "user content: maintenance windows and mutes people create"),
      Map.entry("alerts_delivery", "runtime log: every delivery attempt"),
      Map.entry("alerts_notification", "user content and producer messages"),
      Map.entry("alerts_notification_recipient", "runtime: one per person a notification reaches"),
      Map.entry("alerts_notification_preference", "user content: a person's per-category choice; defaults apply without a row"),
      Map.entry("alerts_heartbeat", "runtime: written by QAlerts.heartbeat and completed processes"),
      Map.entry("alerts_escalation_policy", "host-specific: names real people, teams and channels"),
      Map.entry("alerts_escalation_step", "host-specific: names real people, teams and channels"));



   /*******************************************************************************
    ** Every table is used by runtime code.
    *******************************************************************************/
   @Test
   void everyTableIsUsedAtRuntime() throws Exception
   {
      Map<String, String> entityByTable = entityByTable();
      List<String>        runtimeSource;
      try(Stream<Path> files = Files.walk(MAIN))
      {
         runtimeSource = files.filter(p -> p.toString().endsWith(".java"))
            .filter(p -> !p.startsWith(MAIN.resolve("model")) && !p.startsWith(MAIN.resolve("metadata")) && !p.startsWith(MAIN.resolve("home")) && !p.endsWith("AlertsQBitConfig.java"))
            .map(AlertsTablesGuardrailTest::read).toList();
      }
      for(String table : alertsTables())
      {
         String entity = entityByTable.get(table);
         assertThat(entity).as("entity class for " + table).isNotNull();
         Pattern use = Pattern.compile("\\b" + entity + "(\\.TABLE_NAME|\\.class|\\(|::)");
         assertThat(runtimeSource.stream().anyMatch(source -> use.matcher(source).find()))
            .as(table + " (" + entity + ") must be read or written by runtime code, not only declared").isTrue();
      }
   }



   /*******************************************************************************
    ** Every table is seeded or explained.
    *******************************************************************************/
   @Test
   void everyTableIsSeededOrExplained() throws Exception
   {
      String changelog = AlertsDefaultsChangelogTest.changelogText();
      for(String table : alertsTables())
      {
         boolean seeded = AlertsDefaultsChangelogTest.SEEDED_TABLES.contains(table);
         assertThat(seeded || STARTS_EMPTY.containsKey(table)).as(table + " must be seeded by the defaults changelog or listed in STARTS_EMPTY").isTrue();
         assertThat(seeded && STARTS_EMPTY.containsKey(table)).as(table + " cannot be both seeded and listed as starting empty").isFalse();
         assertThat(changelog.contains("INSERT INTO " + table + " ")).as(table + " seeded iff the changelog inserts it").isEqualTo(seeded);
      }
      assertThat(STARTS_EMPTY.keySet()).allMatch(table -> qInstance.getTable(table) != null, "STARTS_EMPTY names only real tables");
   }



   /*******************************************************************************
    ** Every table and the app have bundle help that says how rows get created.
    *******************************************************************************/
   @Test
   void everyTableHasEmptyStateHelp() throws Exception
   {
      assertThat(AlertsHelp.entries()).allSatisfy((key, entry) -> assertThat(entry.path("summary").asText().length()).as(key).isLessThanOrEqualTo(120));
      for(String table : alertsTables())
      {
         List<QHelpContent> help = qInstance.getTable(table).getHelpContent() == null ? null : qInstance.getTable(table).getHelpContent().get(AlertsHelp.SLOT);
         assertThat(help).as(table + " table help").isNotEmpty();
         assertThat(help.get(0).getContent()).as(table + " help says how rows get created").contains("How rows get created");
      }
      assertThat(qInstance.getApp(AlertsQBitConfig.APP_NAME).getHelpContent()).containsKey(AlertsHelp.SLOT);
      assertThat(AlertsHelp.entries().keySet()).allMatch(key -> !key.startsWith("table:") || qInstance.getTable(key.substring(6, key.indexOf(';'))) != null,
         "help names only real tables");
   }



   /*******************************************************************************
    ** A host's own help on a table is kept; the bundle fills only empty slots.
    *******************************************************************************/
   @Test
   void hostHelpIsNotOverridden() throws Exception
   {
      QTableMetaData table = qInstance.getTable("alerts_channel");
      table.getHelpContent().remove(AlertsHelp.SLOT);
      table.withHelpContent(AlertsHelp.SLOT, new QHelpContent().withContentAsMarkdown("host text"));
      AlertsHelp.apply(qInstance);
      assertThat(table.getHelpContent().get(AlertsHelp.SLOT)).singleElement().matches(c -> "host text".equals(c.getContent()));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private List<String> alertsTables()
   {
      List<String> tables = qInstance.getTables().values().stream().map(QTableMetaData::getName).filter(n -> n.startsWith("alerts_")).sorted().toList();
      assertThat(tables).hasSizeGreaterThan(15);
      return (tables);
   }



   /*******************************************************************************
    ** model/X.java declares TABLE_NAME = "x".
    *******************************************************************************/
   private static Map<String, String> entityByTable() throws Exception
   {
      Map<String, String> rs = new HashMap<>();
      Pattern             p  = Pattern.compile("TABLE_NAME\\s*=\\s*\"([a-z_]+)\"");
      try(Stream<Path> files = Files.list(MAIN.resolve("model")))
      {
         for(Path file : files.toList())
         {
            Matcher m = p.matcher(read(file));
            if(m.find())
            {
               rs.put(m.group(1), file.getFileName().toString().replace(".java", ""));
            }
         }
      }
      return (rs);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static String read(Path path)
   {
      try
      {
         return (Files.readString(path, StandardCharsets.UTF_8));
      }
      catch(Exception e)
      {
         throw (new IllegalStateException(e));
      }
   }

}
