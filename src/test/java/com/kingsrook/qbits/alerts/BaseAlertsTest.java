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
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import com.kingsrook.qbits.alerts.engine.AlertsDb;
import com.kingsrook.qbits.alerts.engine.AlertsProcessListener;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.instances.QInstanceValidator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QAuthenticationType;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.authentication.QAuthenticationMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldType;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.PermissionLevel;
import com.kingsrook.qqq.backend.core.model.metadata.permissions.QPermissionRules;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QBackendStepMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.processes.QProcessMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.tables.QTableMetaData;
import com.kingsrook.qqq.backend.core.model.session.QSession;
import com.kingsrook.qqq.backend.core.model.session.QUser;
import com.kingsrook.qqq.backend.core.modules.authentication.QAuthenticationModuleCustomizerInterface;
import com.kingsrook.qqq.backend.module.rdbms.jdbc.ConnectionManager;
import com.kingsrook.qqq.backend.module.rdbms.model.metadata.RDBMSBackendMetaData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;


/*******************************************************************************
 ** Test base: an H2 backend holding the qbit's tables (created from the same
 ** schema as the Liquibase changelog) and a "widget" subject table, the qbit
 ** produced into a validated QInstance, a settable clock, and sessions by user
 ** id prefix: admin-* (every alerts permission), manager-*, viewer-* (with
 ** read on widget), outsider-* (alerts.view but no widget read), nobody.
 *******************************************************************************/
public class BaseAlertsTest
{
   public static final String BACKEND_NAME = "h2";
   public static final String WIDGET       = "widget";
   public static final String ADMIN        = "admin-1";
   public static final String MANAGER      = "manager-1";
   public static final String VIEWER       = "viewer-1";
   public static final String OUTSIDER     = "outsider-1";

   protected QInstance        qInstance;
   protected AlertsQBitConfig config;
   protected AlertsRuntime    runtime;
   protected MutableClock     clock;



   /*******************************************************************************
    ** Build the instance and database; log in as the admin.
    *******************************************************************************/
   @BeforeEach
   void baseSetUp() throws Exception
   {
      AlertsPreferences.reset();
      AlertsProcessListener.clearCache();

      qInstance = new QInstance();
      qInstance.addBackend(defineH2Backend());
      qInstance.withInstanceDefaultAuthentication(new QAuthenticationMetaData().withName("mock").withType(QAuthenticationType.MOCK)
         .withCustomizer(new QCodeReference(TestSessionCustomizer.class)));

      qInstance.addTable(new QTableMetaData()
         .withName(WIDGET)
         .withLabel("Widget")
         .withBackendName(BACKEND_NAME)
         .withPrimaryKeyField("id")
         .withPermissionRules(new QPermissionRules().withLevel(PermissionLevel.READ_INSERT_EDIT_DELETE_PERMISSIONS))
         .withField(new QFieldMetaData("id", QFieldType.INTEGER).withIsEditable(false))
         .withField(new QFieldMetaData("name", QFieldType.STRING))
         .withField(new QFieldMetaData("status", QFieldType.STRING))
         .withField(new QFieldMetaData("price", QFieldType.DECIMAL))
         .withField(new QFieldMetaData("checkedAt", QFieldType.DATE_TIME).withBackendName("checked_at")));

      qInstance.addProcess(new QProcessMetaData().withName("nightlyThing").withLabel("Nightly thing")
         .withStep(new QBackendStepMetaData().withName("run").withCode(new QCodeReference(TestSteps.Flaky.class))));

      config = new AlertsQBitConfig().withBackendName(BACKEND_NAME).withSynchronousSink(true).withPublicBaseUrl("https://admin.example.com");
      customizeConfig(config);
      new AlertsQBitProducer().withQBitConfig(config).produce(qInstance);
      customizeInstance(qInstance);
      new QInstanceValidator().validate(qInstance);

      runtime = config.getRuntime();
      clock = new MutableClock(Instant.parse("2026-10-12T12:00:00Z"));
      runtime.setClock(clock);

      primeDatabase();
      loginAs(ADMIN);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @AfterEach
   void baseTearDown()
   {
      QContext.clear();
   }



   /*******************************************************************************
    ** Hook: change the config before production.
    *******************************************************************************/
   protected void customizeConfig(AlertsQBitConfig config)
   {
   }



   /*******************************************************************************
    ** Hook: add to the instance before validation.
    *******************************************************************************/
   protected void customizeInstance(QInstance qInstance)
   {
   }



   /*******************************************************************************
    ** The H2 backend.
    *******************************************************************************/
   public static RDBMSBackendMetaData defineH2Backend()
   {
      RDBMSBackendMetaData backend = new RDBMSBackendMetaData()
         .withName(BACKEND_NAME)
         .withVendor("h2")
         .withHostName("mem")
         .withDatabaseName("alerts_test")
         .withUsername("sa");
      backend.setJdbcUrl(ConnectionManager.getJdbcUrl(backend) + ";DATABASE_TO_UPPER=FALSE");
      return (backend);
   }



   /*******************************************************************************
    ** Create the tables (dropping old ones).
    *******************************************************************************/
   protected void primeDatabase() throws Exception
   {
      String script;
      try(InputStream in = BaseAlertsTest.class.getClassLoader().getResourceAsStream("alerts-h2.sql"))
      {
         script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }
      script += "\nDROP TABLE IF EXISTS widget;\nCREATE TABLE widget (id INTEGER AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100), status VARCHAR(20), price DECIMAL(12,2), checked_at TIMESTAMP);\n";

      List<String>  statements = new ArrayList<>();
      StringBuilder current    = new StringBuilder();
      for(String line : script.split("\n"))
      {
         if(line.trim().startsWith("--"))
         {
            continue;
         }
         current.append(line).append('\n');
         if(line.trim().endsWith(";"))
         {
            statements.add(current.toString().trim().replaceAll(";$", ""));
            current.setLength(0);
         }
      }
      try(Connection connection = ConnectionManager.getConnection((RDBMSBackendMetaData) qInstance.getBackend(BACKEND_NAME)); Statement statement = connection.createStatement())
      {
         for(String sql : statements)
         {
            statement.execute(sql);
         }
      }
   }



   /*******************************************************************************
    ** Log in as a test user (session per the prefix rules, alerts keys applied).
    *******************************************************************************/
   protected QSession loginAs(String userId)
   {
      QSession session = TestSessionCustomizer.sessionFor(userId);
      QContext.init(qInstance, session);
      AlertsSecurity.applyToSession(qInstance, session);
      return (session);
   }



   /*******************************************************************************
    ** Insert widgets with these statuses (as the system).
    *******************************************************************************/
   protected List<Integer> insertWidgets(String... statuses) throws QException
   {
      return (runtime.asSystem(() ->
      {
         List<Integer> ids = new ArrayList<>();
         for(String status : statuses)
         {
            ids.add(AlertsDb.insert(WIDGET, new QRecord().withValue("name", "Widget " + status).withValue("status", status).withValue("price", 10)
               .withValue("checkedAt", clock.instant())).getValueInteger("id"));
         }
         return (ids);
      }));
   }



   /*******************************************************************************
    ** All rows of a table (as the system).
    *******************************************************************************/
   protected List<QRecord> all(String tableName) throws QException
   {
      return (runtime.asSystem(() -> AlertsDb.query(tableName, new QQueryFilter())));
   }



   /*******************************************************************************
    ** Rows of a table matching a filter (as the system).
    *******************************************************************************/
   protected List<QRecord> where(String tableName, QQueryFilter filter) throws QException
   {
      return (runtime.asSystem(() -> AlertsDb.query(tableName, filter)));
   }



   /*******************************************************************************
    ** A clock tests can move.
    *******************************************************************************/
   public static class MutableClock extends Clock
   {
      private Instant now;



      /*******************************************************************************
       **
       *******************************************************************************/
      public MutableClock(Instant now)
      {
         this.now = now;
      }



      /*******************************************************************************
       ** Set the time.
       *******************************************************************************/
      public void set(Instant instant)
      {
         now = instant;
      }



      /*******************************************************************************
       ** Move forward.
       *******************************************************************************/
      public void advance(Duration duration)
      {
         now = now.plus(duration);
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public ZoneId getZone()
      {
         return (ZoneId.of("UTC"));
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public Clock withZone(ZoneId zone)
      {
         return (this);
      }



      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public Instant instant()
      {
         return (now);
      }
   }



   /*******************************************************************************
    ** Grants permissions by the user id's prefix.
    *******************************************************************************/
   public static class TestSessionCustomizer implements QAuthenticationModuleCustomizerInterface
   {
      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public void customizeSession(QInstance qInstance, QSession qSession, Map<String, Object> context)
      {
         QSession granted = sessionFor(qSession.getUser() == null ? qSession.getUuid() : qSession.getUser().getIdReference());
         qSession.setUser(granted.getUser());
         qSession.setPermissions(granted.getPermissions());
         AlertsSecurity.applyToSession(qInstance, qSession);
      }



      /*******************************************************************************
       ** Automated sessions (the router's visibility check) get the test
       ** user's permissions for the known prefixes; other ids get none (the
       ** "permissions unknown" path).
       *******************************************************************************/
      @Override
      public void customizeAutomatedSessionForUser(QInstance qInstance, QSession automatedSessionForUser, java.io.Serializable userId)
      {
         String id = String.valueOf(userId);
         if(id.startsWith("admin") || id.startsWith("manager") || id.startsWith("viewer") || id.startsWith("outsider"))
         {
            automatedSessionForUser.setPermissions(sessionFor(id).getPermissions());
         }
      }



      /*******************************************************************************
       ** A session for a test user.
       *******************************************************************************/
      public static QSession sessionFor(String userId)
      {
         AlertsQBitConfig defaults = new AlertsQBitConfig();
         QSession         session  = new QSession().withUser(new QUser().withIdReference(userId).withFullName("Name of " + userId));
         session.setPermissions(new HashSet<>());
         if(userId == null)
         {
            return (session);
         }
         if(userId.startsWith("admin"))
         {
            session.getPermissions().addAll(defaults.getAdminPermissionNames());
         }
         else if(userId.startsWith("manager"))
         {
            session.getPermissions().addAll(defaults.getManagerPermissionNames());
         }
         else if(userId.startsWith("viewer") || userId.startsWith("outsider"))
         {
            session.getPermissions().addAll(defaults.getViewerPermissionNames());
         }
         if(!userId.startsWith("outsider") && !userId.startsWith("nobody"))
         {
            session.getPermissions().addAll(List.of(WIDGET + ".read", WIDGET + ".insert", WIDGET + ".edit", WIDGET + ".delete"));
         }
         return (session);
      }
   }
}
