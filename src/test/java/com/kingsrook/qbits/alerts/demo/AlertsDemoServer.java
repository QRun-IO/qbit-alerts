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

package com.kingsrook.qbits.alerts.demo;


import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.alerts.AlertsPreferences;
import com.kingsrook.qbits.alerts.AlertsQBitConfig;
import com.kingsrook.qbits.alerts.AlertsQBitProducer;
import com.kingsrook.qbits.alerts.AlertsRuntime;
import com.kingsrook.qbits.alerts.AlertsSecurity;
import com.kingsrook.qbits.alerts.BaseAlertsTest;
import com.kingsrook.qbits.alerts.engine.AlertsDb;
import com.kingsrook.qbits.alerts.model.AlertsChannel;
import com.kingsrook.qbits.alerts.model.AlertsMonitor;
import com.kingsrook.qbits.alerts.model.AlertsSubscription;
import com.kingsrook.qbits.alerts.model.AlertsType;
import com.kingsrook.qbits.alerts.model.AlertsUserPreference;
import com.kingsrook.qbits.alerts.notifications.NotificationService;
import com.kingsrook.qqq.backend.core.alerts.AlertDefaultMonitor;
import com.kingsrook.qqq.backend.core.alerts.AlertLink;
import com.kingsrook.qqq.backend.core.alerts.AlertMonitorKind;
import com.kingsrook.qqq.backend.core.alerts.AlertRecordRef;
import com.kingsrook.qqq.backend.core.alerts.AlertSeverity;
import com.kingsrook.qqq.backend.core.alerts.AlertSignal;
import com.kingsrook.qqq.backend.core.alerts.AlertsInstanceMetaData;
import com.kingsrook.qqq.backend.core.alerts.NotificationAudience;
import com.kingsrook.qqq.backend.core.alerts.NotificationCategory;
import com.kingsrook.qqq.backend.core.alerts.NotificationSeverity;
import com.kingsrook.qqq.backend.core.alerts.NotificationSignal;
import com.kingsrook.qqq.backend.core.alerts.QAlertTypeMetaData;
import com.kingsrook.qqq.backend.core.alerts.ResolveMode;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.instances.AbstractQQQApplication;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QAuthenticationType;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.QAuthenticationMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.branding.QBrandingMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QAppMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.layout.QIcon;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.PermissionLevel;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.QPermissionRules;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.model.session.QSystemUserSession;
import com.kingsrook.qqq.backend.core.model.session.QUser;
import com.kingsrook.qqq.backend.core.modules.authentication.QAuthenticationModuleCustomizerInterface;
import com.kingsrook.qqq.backend.core.utils.JsonUtils;
import com.kingsrook.qqq.backend.module.rdbms.jdbc.ConnectionManager;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSBackendMetaData;
import com.kingsrook.qqq.middleware.javalin.QApplicationJavalinServer;


/*******************************************************************************
 ** A local demo of the Alerts qbit for screenshots and manual checks (not a
 ** test, not shipped): an in-memory H2 database, two demo tables (blog posts,
 ** backup runs), a mock login as an alerts admin, and 30 days of history
 ** produced through the real engine (monitors, producer signals, actions).
 **
 ** Run it, then the Next UI dev server against it:
 **   java ... com.kingsrook.qbits.alerts.demo.AlertsDemoServer      (port 8000)
 **   QQQ_BACKEND_URL=http://localhost:8000 pnpm dev                (port 3000)
 *******************************************************************************/
public class AlertsDemoServer extends AbstractQQQApplication
{
   static final String BACKEND = "demo";
   static final String ADMIN   = "dana";



   /*******************************************************************************
    ** Start the demo.
    *******************************************************************************/
   public static void main(String[] args) throws Exception
   {
      AlertsDemoServer application = new AlertsDemoServer();
      new QApplicationJavalinServer(application)
         .withServeFrontendNext(false)
         .withServeFrontendMaterialDashboard(false)
         .withPort(Integer.parseInt(System.getenv().getOrDefault("ALERTS_DEMO_PORT", "8000")))
         .start();
   }



   /*******************************************************************************
    ** The demo instance (seeded on first definition).
    *******************************************************************************/
   @Override
   public QInstance defineQInstance() throws QException
   {
      QInstance qInstance = new QInstance();
      RDBMSBackendMetaData backend = new RDBMSBackendMetaData().withName(BACKEND).withVendor("h2").withHostName("mem").withDatabaseName("alerts_demo").withUsername("sa");
      backend.setJdbcUrl(ConnectionManager.getJdbcUrl(backend) + ";DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1");
      qInstance.addBackend(backend);
      qInstance.withInstanceDefaultAuthentication(new QAuthenticationMetaData().withName("mock").withType(QAuthenticationType.MOCK)
         .withCustomizer(new QCodeReference(DemoSessionCustomizer.class)));
      qInstance.setBranding(new QBrandingMetaData().withAppName("Alerts demo").withCompanyName("QRun-IO"));

      QTableMetaData posts = new QTableMetaData().withName("blog_post").withLabel("Blog Post").withBackendName(BACKEND).withPrimaryKeyField("id")
         .withIcon(new QIcon("article")).withRecordLabelFormat("%s").withRecordLabelFields("title")
         .withPermissionRules(new QPermissionRules().withLevel(PermissionLevel.READ_INSERT_EDIT_DELETE_PERMISSIONS))
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("title", QFieldType.STRING))
         .withField(new QFieldMetaData("excerpt", QFieldType.STRING))
         .withField(new QFieldMetaData("status", QFieldType.STRING));
      QTableMetaData runs = new QTableMetaData().withName("backup_run").withLabel("Backup Run").withBackendName(BACKEND).withPrimaryKeyField("id")
         .withIcon(new QIcon("backup")).withRecordLabelFormat("%s").withRecordLabelFields("jobName")
         .withPermissionRules(new QPermissionRules().withLevel(PermissionLevel.READ_INSERT_EDIT_DELETE_PERMISSIONS))
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("jobName", QFieldType.STRING).withBackendName("job_name"))
         .withField(new QFieldMetaData("status", QFieldType.STRING))
         .withField(new QFieldMetaData("finishedAt", QFieldType.DATE_TIME).withBackendName("finished_at"));
      qInstance.addTable(posts);
      qInstance.addTable(runs);
      qInstance.addApp(new QAppMetaData().withName("content").withLabel("Content").withIcon(new QIcon("article")).withChild(posts).withChild(runs));

      AlertsInstanceMetaData.ofOrWithNew(qInstance)
         .withAlertType(new QAlertTypeMetaData().withName("content.post.no-excerpt").withLabel("Published post has no excerpt").withSourceName("qbit-cms")
            .withGroupName("content").withDefaultSeverity(AlertSeverity.WARNING).withSubjectTableName("blog_post").withResolveMode(ResolveMode.MONITOR)
            .withDescription("A published post has no excerpt, so search results and social cards show nothing.")
            .withHowToFix("Open the post, write a one-sentence excerpt, and save.")
            .withDefaultMonitor(new AlertDefaultMonitor().withName("Published posts without an excerpt").withKind(AlertMonitorKind.TABLE_QUERY).withPerRecord(true)
               .withDefinitionValue("tableName", "blog_post").withDefinitionValue("labelField", "title")
               .withDefinitionValue("filter", (java.io.Serializable) Map.of("criteria", List.of(Map.of("fieldName", "status", "operator", "EQUALS", "values", List.of("PUBLISHED")),
                  Map.of("fieldName", "excerpt", "operator", "IS_BLANK", "values", List.of()))))))
         .withAlertType(new QAlertTypeMetaData().withName("backups.job.failed").withLabel("Backup job failed").withSourceName("qbit-backups")
            .withGroupName("backups").withDefaultSeverity(AlertSeverity.ERROR).withSubjectTableName("backup_run")
            .withDescription("A backup job's last run failed.").withHowToFix("Open the run's log, fix the cause, and run the job again."))
         .withAlertType(new QAlertTypeMetaData().withName("backups.job.missed").withLabel("Backup job did not run").withSourceName("qbit-backups")
            .withGroupName("backups").withDefaultSeverity(AlertSeverity.CRITICAL).withResolveMode(ResolveMode.MONITOR)
            .withDescription("No backup completed within its expected interval.").withHowToFix("Check the scheduler and the job's last runs."))
         .withAlertType(new QAlertTypeMetaData().withName("cdn.purge.dead").withLabel("CDN purge failed").withSourceName("qbit-cdn")
            .withGroupName("cdn").withDefaultSeverity(AlertSeverity.ERROR)
            .withDescription("A purge failed after its last retry.").withHowToFix("Open the failed purge and choose Retry purge."))
         .withAlertType(new QAlertTypeMetaData().withName("storage.quota.near").withLabel("Storage near quota").withSourceName("qbit-object-storage")
            .withGroupName("storage").withDefaultSeverity(AlertSeverity.INFO).withQuietPeriod(Duration.ofHours(24))
            .withDescription("A bucket is over 80% of its quota.").withHowToFix("Remove orphaned objects or raise the quota."));

      new AlertsQBitProducer().withQBitConfig(new AlertsQBitConfig().withBackendName(BACKEND).withSynchronousSink(true).withPublicBaseUrl("http://localhost:3000")
         .withFromAddress("alerts@example.com")).produce(qInstance);

      seed(qInstance);
      return (qInstance);
   }



   /*******************************************************************************
    ** User notifications for the bell, the page and the admin screens: an
    ** announcement, a sticky security notice, a failed job, a share, a
    ** marketing note, a draft and a scheduled one.
    *******************************************************************************/
   static void seedNotifications(AlertsRuntime runtime, BaseAlertsTest.MutableClock clock) throws QException
   {
      Instant now = Instant.now();
      runtime.asSystem(() ->
      {
         AlertsDb.insert("alerts_user_preference", new QRecord().withValue("userId", "sam").withValue("userLabel", "Sam Rivera").withValue("inAppEnabled", true)
            .withValue("emailEnabled", false).withValue("digestHour", 8));
         return (null);
      });
      NotificationService service = runtime.getNotifications();
      clock.set(now.minus(Duration.ofDays(3)));
      service.notify(new NotificationSignal("Version 4.1 is out").withBody("**What's new:** a notification center in the top bar, record pickers with thumbnails, "
            + "and dark mode for the SQL Workbench. " + "\n\n" + "See the release notes for everything else.")
         .withSeverity(NotificationSeverity.SUCCESS).withCategory(NotificationCategory.RELEASE).withSourceName("admin")
         .withLink(AlertLink.toUrl("https://github.com/QRun-IO/qqq/releases", "Release notes")).withAudience(NotificationAudience.allUsers()));
      clock.set(now.minus(Duration.ofDays(1)));
      service.notify(new NotificationSignal("Spring content review starts Monday").withBody("Editors: please review your top 10 posts' excerpts and images before the campaign.")
         .withCategory(NotificationCategory.MARKETING).withSourceName("qbit-marketing").withAudience(NotificationAudience.allUsers()));
      clock.set(now.minus(Duration.ofHours(5)));
      service.notify(new NotificationSignal("Sam shared the notebook \"Q3 traffic\" with you").withCategory(NotificationCategory.SHARE).withSourceName("qbit-sql-workbench")
         .withLink(AlertLink.toPath("/app/blog_post", "Open notebook")).withAudience(NotificationAudience.user(ADMIN)));
      clock.set(now.minus(Duration.ofMinutes(40)));
      service.notify(new NotificationSignal("Blog post import finished with errors").withBody("3 rows had errors: the publish date was not a date. The other 117 rows were imported.")
         .withSeverity(NotificationSeverity.WARNING).withCategory(NotificationCategory.JOB).withSourceName("processes").withSourceKey("process:demo-import")
         .withLink(AlertLink.toPath("/app/blog_post", "Open Blog Posts")).withAudience(NotificationAudience.user(ADMIN)));
      clock.set(now.minus(Duration.ofMinutes(8)));
      service.notify(new NotificationSignal("New sign-in to your account").withBody("A sign-in from Firefox on Windows near Columbus, OH. If this wasn't you, change your password now.")
         .withSeverity(NotificationSeverity.CRITICAL).withCategory(NotificationCategory.SECURITY).withSourceName("security").withSticky(true)
         .withAudience(NotificationAudience.user(ADMIN)));
      clock.set(now);
      runtime.asSystem(() ->
      {
         AlertsDb.insert("alerts_notification", new QRecord().withValue("title", "Planned maintenance Saturday 06:00 UTC").withValue("body", "The admin is read-only for about 15 minutes.")
            .withValue("severity", "WARNING").withValue("category", "SYSTEM").withValue("status", "DRAFT").withValue("audienceType", "ALL_USERS").withValue("authorUserId", ADMIN)
            .withValue("sourceName", "admin").withValue("sticky", false));
         AlertsDb.insert("alerts_notification", new QRecord().withValue("title", "Holiday hours").withValue("body", "Support answers within one business day over the holidays.")
            .withValue("severity", "INFO").withValue("category", "SYSTEM").withValue("status", "SCHEDULED").withValue("audienceType", "ROLES").withValue("audienceValue", "staff")
            .withValue("publishAt", now.plus(Duration.ofDays(10))).withValue("authorUserId", ADMIN).withValue("sourceName", "admin").withValue("sticky", false));
         return (null);
      });
   }



   /*******************************************************************************
    ** Create the tables and produce 30 days of history through the engine.
    *******************************************************************************/
   static void seed(QInstance qInstance) throws QException
   {
      try
      {
         String script;
         try(InputStream in = BaseAlertsTest.class.getClassLoader().getResourceAsStream("alerts-h2.sql"))
         {
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
         }
         script += "\nCREATE TABLE IF NOT EXISTS blog_post (id INTEGER AUTO_INCREMENT PRIMARY KEY, title VARCHAR(200), excerpt VARCHAR(500), status VARCHAR(20));\n"
            + "CREATE TABLE IF NOT EXISTS backup_run (id INTEGER AUTO_INCREMENT PRIMARY KEY, job_name VARCHAR(100), status VARCHAR(20), finished_at TIMESTAMP);\n";
         try(Connection connection = ConnectionManager.getConnection((RDBMSBackendMetaData) qInstance.getBackend(BACKEND)); Statement statement = connection.createStatement())
         {
            for(String sql : script.replaceAll("(?m)^--.*$", "").split(";\\s*\\n"))
            {
               if(!sql.isBlank())
               {
                  statement.execute(sql.trim());
               }
            }
         }
      }
      catch(Exception e)
      {
         throw (new QException("Could not create the demo tables", e));
      }

      QContext.init(qInstance, new QSystemUserSession());
      AlertsRuntime runtime = AlertsRuntime.get(qInstance);
      BaseAlertsTest.MutableClock clock = new BaseAlertsTest.MutableClock(Instant.now().minus(Duration.ofDays(30)));
      runtime.setClock(clock);
      runtime.getTypeSync().syncIfNeeded();

      runtime.asSystem(() ->
      {
         for(String[] post : new String[][] {{"Spring release notes", "What changed this spring.", "PUBLISHED"}, {"How we test our backups", "", "PUBLISHED"},
            {"Draft: pricing update", "", "DRAFT"}, {"Five tips for faster pages", "", "PUBLISHED"}, {"Welcome to the new site", "A short tour.", "PUBLISHED"}})
         {
            AlertsDb.insert("blog_post", new QRecord().withValue("title", post[0]).withValue("excerpt", post[1].isEmpty() ? null : post[1]).withValue("status", post[2]));
         }
         return (null);
      });

      QSession admin = DemoSessionCustomizer.sessionFor(ADMIN);
      QContext.init(qInstance, admin);
      AlertsSecurity.applyToSession(qInstance, admin);
      AlertsPreferences.ensure(runtime, admin);
      QContext.init(qInstance, new QSystemUserSession());

      runtime.asSystem(() ->
      {
         QRecord preference = AlertsDb.first(AlertsUserPreference.TABLE_NAME, AlertsDb.eq("userId", ADMIN));
         AlertsDb.update(AlertsUserPreference.TABLE_NAME, new QRecord().withValue("id", preference.getValue("id")).withValue("emailAddress", "dana@example.com"));
         QRecord list = AlertsDb.insert(AlertsChannel.TABLE_NAME, new QRecord().withValue("name", "ops-list").withValue("label", "Ops email list").withValue("channelType", "EMAIL_LIST")
            .withValue("enabled", true).withValue("settings", JsonUtils.toJson(Map.of("addresses", List.of("ops@example.com")))).withValue("detailLevel", "MINIMAL")
            .withValue("minSeverity", "ERROR").withValue("rateLimitPerHour", 60).withValue("healthStatus", "HEALTHY").withValue("consecutiveFailures", 0));
         AlertsDb.insert(AlertsSubscription.TABLE_NAME, new QRecord().withValue("subscriberType", "CHANNEL").withValue("channelId", list.getValue("id")).withValue("scopeType", "GROUP")
            .withValue("scopeValue", "backups").withValue("minSeverity", "ERROR").withValue("events", "RAISED,RESOLVED").withValue("deliveryMode", "IMMEDIATE").withValue("enabled", true));
         AlertsDb.insert(AlertsSubscription.TABLE_NAME, new QRecord().withValue("subscriberType", "USER").withValue("userId", ADMIN).withValue("scopeType", "GROUP")
            .withValue("scopeValue", "content").withValue("minSeverity", "WARNING").withValue("events", "RAISED,RESOLVED").withValue("deliveryMode", "IMMEDIATE").withValue("enabled", true));
         AlertsDb.insert(AlertsMonitor.TABLE_NAME, new QRecord().withValue("name", "Nightly backup heartbeat").withValue("kind", "HEARTBEAT").withValue("enabled", true)
            .withValue("alertTypeId", AlertsDb.first(AlertsType.TABLE_NAME, AlertsDb.eq("name", "backups.job.missed")).getValue("id"))
            .withValue("definition", JsonUtils.toJson(Map.of("heartbeatKey", "backups.nightly-db", "expectedEverySeconds", 86400, "graceSeconds", 3600)))
            .withValue("scheduleSeconds", 600).withValue("forMinutes", 0).withValue("consecutiveFailures", 0).withValue("perRecord", false));
         return (null);
      });

      ////////////////////////////////////////////////////////////////////////
      // 30 days: nightly backups (two failures, a missed night at the end), //
      // CDN purge failures that clear, storage near quota, monitor checks   //
      ////////////////////////////////////////////////////////////////////////
      for(int day = 0; day < 30; day++)
      {
         clock.set(Instant.now().minus(Duration.ofDays(30 - day)).plus(Duration.ofHours(2)));
         final int d = day;
         if(day < 29)
         {
            Integer runId = runtime.asSystem(() -> AlertsDb.insert("backup_run", new QRecord().withValue("jobName", "nightly-db").withValue("status", d == 11 || d == 23 ? "FAILED" : "OK")
               .withValue("finishedAt", clock.instant())).getValueInteger("id"));
            if(day == 11 || day == 23)
            {
               runtime.getSink().raise(new AlertSignal("backups.job.failed").withDedupKey("nightly-db").withTitle("Nightly database backup failed")
                  .withSummary("The dump step exited with an error.").withDetails("pg_dump: error: connection to server lost")
                  .withRecordRef(AlertRecordRef.of("backup_run", runId)).withLink(com.kingsrook.qqq.backend.core.alerts.AlertLink.toRecord("backup_run", runId, "Open the run")));
            }
            else
            {
               runtime.getSink().resolve(new com.kingsrook.qqq.backend.core.alerts.AlertResolution("backups.job.failed", "nightly-db", "The next run succeeded"));
               runtime.getSink().heartbeat("backups.nightly-db", clock.instant());
            }
         }
         if(day % 4 == 1 || day == 27)
         {
            runtime.getSink().raise(new AlertSignal("cdn.purge.dead").withDedupKey("provider:cloudfront").withTitle((2 + day % 3) + " CDN purges failed after retries")
               .withSummary("Content will correct itself when the edge TTL expires.").withCount(2 + day % 3));
            if(day != 27)
            {
               clock.set(clock.instant().plus(Duration.ofHours(3)));
               runtime.getSink().resolve(new com.kingsrook.qqq.backend.core.alerts.AlertResolution("cdn.purge.dead", "provider:cloudfront", "A later purge succeeded"));
            }
         }
         if(day == 25)
         {
            runtime.getSink().raise(new AlertSignal("storage.quota.near").withDedupKey("bucket:media").withTitle("Media bucket is at 84% of its quota").withCount(84));
         }
         if(day % 7 == 0 || day == 29)
         {
            runtime.getEvaluator().evaluateDue();
         }
      }

      clock.set(Instant.now());
      runtime.getEvaluator().evaluateDue();
      QRecord acked = runtime.asSystem(() -> AlertsDb.first("alerts_alert", AlertsDb.eq("alertTypeName", "content.post.no-excerpt")));
      if(acked != null)
      {
         runtime.getEngine().acknowledge(acked.getValueInteger("id"), ADMIN);
         runtime.getEngine().comment(acked.getValueInteger("id"), ADMIN, "Asked the author for an excerpt.");
      }
      runtime.getDeliveryWorker().deliverDue();
      seedNotifications(runtime, clock);
      runtime.setClock(Clock.systemUTC());
      QContext.clear();
   }



   /*******************************************************************************
    ** Every mock session is Dana, an alerts admin who can read the demo tables.
    *******************************************************************************/
   public static class DemoSessionCustomizer implements QAuthenticationModuleCustomizerInterface
   {
      /***************************************************************************
       **
       ***************************************************************************/
      @Override
      public void customizeSession(QInstance qInstance, QSession qSession, Map<String, Object> context)
      {
         QSession granted = sessionFor(ADMIN);
         qSession.setUser(granted.getUser());
         qSession.setPermissions(granted.getPermissions());
         AlertsSecurity.applyToSession(qInstance, qSession);
      }



      /***************************************************************************
       **
       ***************************************************************************/
      @Override
      public void customizeAutomatedSessionForUser(QInstance qInstance, QSession automatedSessionForUser, java.io.Serializable userId)
      {
         automatedSessionForUser.setPermissions(sessionFor(String.valueOf(userId)).getPermissions());
      }



      /***************************************************************************
       ** Dana's session.
       ***************************************************************************/
      static QSession sessionFor(String userId)
      {
         QSession session = new QSession().withUser(new QUser().withIdReference(userId).withFullName("Dana Admin"));
         session.setPermissions(new HashSet<>(new AlertsQBitConfig().getAdminPermissionNames()));
         for(String table : List.of("blog_post", "backup_run"))
         {
            for(String suffix : List.of("read", "insert", "edit", "delete"))
            {
               session.getPermissions().add(table + "." + suffix);
            }
         }
         return (session);
      }
   }
}
