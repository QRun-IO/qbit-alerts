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


package com.kingsrook.qbits.alerts.notifications;


import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.kingsrook.qbits.alerts.AlertsPreferences;
import com.kingsrook.qbits.alerts.AlertsRuntime;
import com.kingsrook.qbits.alerts.BaseAlertsTest;
import com.kingsrook.qbits.alerts.api.AlertsApi;
import com.kingsrook.qbits.alerts.engine.AlertsDb;
import com.kingsrook.qbits.alerts.engine.QbitAlertsSink;
import com.kingsrook.qbits.alerts.model.AlertsNotification;
import com.kingsrook.qbits.alerts.model.AlertsNotificationPreference;
import com.kingsrook.qbits.alerts.model.AlertsNotificationRecipient;
import com.kingsrook.qbits.alerts.processes.AlertsAction;
import com.kingsrook.qbits.alerts.spi.AlertLink;
import com.kingsrook.qbits.alerts.spi.NotificationAudience;
import com.kingsrook.qbits.alerts.spi.NotificationCategory;
import com.kingsrook.qbits.alerts.spi.NotificationSeverity;
import com.kingsrook.qbits.alerts.spi.NotificationSignal;
import com.kingsrook.qqq.backend.core.actions.processes.QProcessCallback;
import com.kingsrook.qqq.backend.core.actions.processes.RunProcessAction;
import com.kingsrook.qqq.backend.core.actions.tables.DeleteAction;
import com.kingsrook.qqq.backend.core.actions.tables.InsertAction;
import com.kingsrook.qqq.backend.core.actions.tables.UpdateAction;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLine;
import com.kingsrook.qqq.backend.core.model.actions.processes.ProcessSummaryLineInterface;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessInput;
import com.kingsrook.qqq.backend.core.model.actions.processes.RunProcessOutput;
import com.kingsrook.qqq.backend.core.model.actions.tables.delete.DeleteInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.insert.InsertInput;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QCriteriaOperator;
import com.kingsrook.qqq.backend.core.model.actions.tables.query.QQueryFilter;
import com.kingsrook.qqq.backend.core.model.actions.tables.update.UpdateInput;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.fields.QFieldMetaData;
import com.kingsrook.qqq.backend.core.processes.implementations.etl.streamedwithfrontend.StreamedETLWithFrontendProcess;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/*******************************************************************************
 ** User notifications (docs/NOTIFICATIONS.md): authoring, audiences,
 ** recipients, the bell and page API, actions, producers, retention.
 *******************************************************************************/
class NotificationsTest extends BaseAlertsTest
{
   static final String AUTHOR = "author-1";



   /*******************************************************************************
    ** A directory with roles, for ROLES audiences.
    *******************************************************************************/
   public static class TestDirectory implements AlertsUserDirectoryInterface
   {
      /*******************************************************************************
       **
       *******************************************************************************/
      @Override
      public List<DirectoryUser> listUsers()
      {
         return (List.of(
            new DirectoryUser("viewer-1", "Vera Viewer", "vera@example.com", Set.of("staff")),
            new DirectoryUser("viewer-2", "Victor Viewer", null, Set.of("staff", "editors")),
            new DirectoryUser("admin-1", "Ada Admin", null, Set.of("admins")),
            new DirectoryUser("outsider-1", "Otto Outsider", null, Set.of())));
      }
   }



   /*******************************************************************************
    ** Users who have opened the bell (the default directory).
    *******************************************************************************/
   private void knownUsers(String... userIds) throws QException
   {
      for(String userId : userIds)
      {
         AlertsPreferences.ensure(runtime, loginAs(userId));
      }
   }



   /*******************************************************************************
    ** Insert a notification as the current user; returns the record.
    *******************************************************************************/
   private QRecord author(QRecord notification) throws QException
   {
      return (new InsertAction().execute(new InsertInput(AlertsNotification.TABLE_NAME).withRecord(notification)).getRecords().get(0));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   @SuppressWarnings("unchecked")
   private static List<Map<String, Object>> items(Map<String, Object> result)
   {
      return ((List<Map<String, Object>>) result.get("items"));
   }



   /*******************************************************************************
    ** Run an action as the current user; the Result lines.
    *******************************************************************************/
   private List<String> run(AlertsAction action, List<Serializable> ids) throws QException
   {
      RunProcessInput input = new RunProcessInput()
         .withProcessName(action.getProcessName())
         .withFrontendStepBehavior(RunProcessInput.FrontendStepBehavior.SKIP)
         .withCallback(new QProcessCallback()
         {
            @Override
            public QQueryFilter getQueryFilter()
            {
               return (new QQueryFilter().withCriteria("id", QCriteriaOperator.IN, ids));
            }



            @Override
            public Map<String, Serializable> getFieldValues(List<QFieldMetaData> fields)
            {
               return (new HashMap<>());
            }
         });
      RunProcessOutput output = new RunProcessAction().execute(input);
      if(output.getException().isPresent())
      {
         throw (new QException(output.getException().get()));
      }
      @SuppressWarnings("unchecked")
      List<ProcessSummaryLineInterface> lines = (List<ProcessSummaryLineInterface>) output.getValue(StreamedETLWithFrontendProcess.FIELD_PROCESS_SUMMARY);
      List<String> text = new ArrayList<>();
      for(ProcessSummaryLineInterface line : lines == null ? List.<ProcessSummaryLineInterface>of() : lines)
      {
         text.add(line.getStatus() + ": " + (line instanceof ProcessSummaryLine summary ? summary.getCount() + " " + summary.getMessage() : String.valueOf(line)));
      }
      return (text);
   }



   /*******************************************************************************
    ** An author writes, previews, sends, edits and retracts an announcement;
    ** viewers read, unread and dismiss it in the bell.
    *******************************************************************************/
   @Test
   void testAuthorSendReadRetract() throws Exception
   {
      knownUsers("viewer-1", "viewer-2");
      loginAs(AUTHOR);
      QRecord draft = author(new QRecord().withValue("title", "Version 2.0 is out").withValue("body", "**New:** dark mode.")
         .withValue("severity", "SUCCESS").withValue("category", "RELEASE").withValue("audienceType", "ALL_USERS").withValue("linkUrl", "/app/releases"));
      assertThat(draft.getErrors()).isEmpty();
      Integer id = draft.getValueInteger("id");
      QRecord stored = AlertsDb.byId(AlertsNotification.TABLE_NAME, id);
      assertThat(stored.getValueString("status")).isEqualTo("DRAFT");
      assertThat(stored.getValueString("authorUserId")).isEqualTo(AUTHOR);
      assertThat(stored.getValueString("sourceName")).isEqualTo("admin");

      List<String> preview = run(AlertsAction.PREVIEW_AUDIENCE, List.of(id));
      assertThat(String.join("\n", preview)).contains("Would reach 2 users").contains("Name of viewer-1");
      assertThat(runtime.asSystem(() -> all(AlertsNotificationRecipient.TABLE_NAME))).isEmpty();

      List<String> sent = run(AlertsAction.SEND_NOTIFICATION, List.of(id));
      assertThat(String.join("\n", sent)).contains("notification was sent").contains("reached 2 new recipients");
      assertThat(AlertsDb.byId(AlertsNotification.TABLE_NAME, id).getValueString("status")).isEqualTo("SENT");
      assertThat(AlertsDb.byId(AlertsNotification.TABLE_NAME, id).getValueInteger("recipientCount")).isEqualTo(2);

      loginAs("viewer-1");
      AlertsApi api     = new AlertsApi(runtime);
      Map<String, Object> summary = api.inboxSummary();
      assertThat(summary.get("unreadCount")).isEqualTo(1);
      assertThat(summary.get("highestUnreadSeverity")).isEqualTo("SUCCESS");
      Map<String, Object> item = items(summary).get(0);
      assertThat(item).containsEntry("title", "Version 2.0 is out").containsEntry("category", "RELEASE").containsEntry("href", "/app/releases").containsEntry("unread", true);
      assertThat(item).doesNotContainKey("body");
      Integer mine = (Integer) item.get("id");

      ////////////////////////////////////////////////////////////////////
      // another user's row is invisible to me, and can't be changed     //
      ////////////////////////////////////////////////////////////////////
      Integer theirs = runtime.asSystem(() -> AlertsDb.first(AlertsNotificationRecipient.TABLE_NAME, AlertsDb.eq("userId", "viewer-2"))).getValueInteger("id");
      assertThat(AlertsDb.byId(AlertsNotificationRecipient.TABLE_NAME, theirs)).isNull();
      assertThat(api.markRead(Map.of("id", theirs)).get("updated")).isEqualTo(0);

      assertThat(api.markRead(Map.of("id", mine)).get("updated")).isEqualTo(1);
      assertThat(api.inboxSummary().get("unreadCount")).isEqualTo(0);
      assertThat(runtime.asSystem(() -> AlertsDb.byId(AlertsNotification.TABLE_NAME, id)).getValueInteger("readCount")).isEqualTo(1);
      assertThat(api.markUnread(Map.of("ids", List.of(mine))).get("updated")).isEqualTo(1);
      assertThat(api.inboxSummary().get("unreadCount")).isEqualTo(1);
      assertThatThrownBy(() -> api.markUnread(Map.of("all", true))).hasMessageContaining("Send id or ids");

      ////////////////////////////////////////////////////////////////////
      // the page has the body; editing after send changes what I see    //
      ////////////////////////////////////////////////////////////////////
      assertThat(items(api.inbox(Map.of())).get(0).get("body")).isEqualTo("**New:** dark mode.");
      loginAs(AUTHOR);
      new UpdateAction().execute(new UpdateInput(AlertsNotification.TABLE_NAME).withRecord(new QRecord().withValue("id", id).withValue("title", "Version 2.0.1 is out")
         .withValue("status", "DRAFT").withValue("recipientCount", 99)));
      assertThat(AlertsDb.byId(AlertsNotification.TABLE_NAME, id).getValueString("status")).isEqualTo("SENT");
      loginAs("viewer-1");
      assertThat(items(api.inboxSummary()).get(0).get("title")).isEqualTo("Version 2.0.1 is out");

      ////////////////////////////////////////////////////////////////////
      // a user can change only read and dismissed on their row          //
      ////////////////////////////////////////////////////////////////////
      QRecord moved = new UpdateAction().execute(new UpdateInput(AlertsNotificationRecipient.TABLE_NAME).withRecord(new QRecord().withValue("id", mine)
         .withValue("userId", "viewer-2"))).getRecords().get(0);
      assertThat(moved.getErrors()).isNotEmpty();
      new UpdateAction().execute(new UpdateInput(AlertsNotificationRecipient.TABLE_NAME).withRecord(new QRecord().withValue("id", mine)
         .withValue("emailStatus", "SENT").withValue("readAt", Instant.now())));
      QRecord row = AlertsDb.byId(AlertsNotificationRecipient.TABLE_NAME, mine);
      assertThat(row.getValueString("userId")).isEqualTo("viewer-1");
      assertThat(row.getValueString("emailStatus")).isEqualTo("NONE");
      assertThat(row.getValue("readAt")).isNotNull();
      assertThat(new DeleteAction().execute(new DeleteInput(AlertsNotificationRecipient.TABLE_NAME).withPrimaryKeys(List.of(mine))).getDeletedRecordCount()).isEqualTo(0);

      ////////////////////////////////////////////////////////////////////
      // retracting hides it everywhere                                  //
      ////////////////////////////////////////////////////////////////////
      loginAs(AUTHOR);
      assertThat(String.join("\n", run(AlertsAction.RETRACT_NOTIFICATION, List.of(id)))).contains("notification was retracted");
      assertThat(String.join("\n", run(AlertsAction.SEND_NOTIFICATION, List.of(id)))).contains("can't be sent");
      loginAs("viewer-2");
      assertThat(api.inboxSummary().get("unreadCount")).isEqualTo(0);
      assertThat(items(api.inbox(Map.of()))).isEmpty();
   }



   /*******************************************************************************
    ** Dismiss, all, the alertId form, and page filters and paging.
    *******************************************************************************/
   @Test
   void testDismissAndPage() throws Exception
   {
      for(int i = 0; i < 4; i++)
      {
         runtime.getNotifications().notify(new NotificationSignal("Job " + i + " failed").withSeverity(i % 2 == 0 ? NotificationSeverity.ERROR : NotificationSeverity.WARNING)
            .withCategory(NotificationCategory.JOB).withAudience(NotificationAudience.user("viewer-1")));
         clock.advance(Duration.ofMinutes(1));
      }
      runtime.getNotifications().notify(new NotificationSignal("Sign-in from a new device").withCategory(NotificationCategory.SECURITY)
         .withSeverity(NotificationSeverity.CRITICAL).withSticky(true).withAudience(NotificationAudience.user("viewer-1")));

      loginAs("viewer-1");
      AlertsApi           api     = new AlertsApi(runtime);
      Map<String, Object> summary = api.inboxSummary();
      assertThat(summary.get("unreadCount")).isEqualTo(5);
      assertThat(summary.get("highestUnreadSeverity")).isEqualTo("CRITICAL");
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> banners = (List<Map<String, Object>>) summary.get("banners");
      assertThat(banners).singleElement().satisfies(banner -> assertThat(banner).containsEntry("title", "Sign-in from a new device").containsKey("body"));
      assertThat(items(summary).get(0).get("title")).isEqualTo("Sign-in from a new device");

      assertThat(api.inbox(Map.of("category", "job")).get("total")).isEqualTo(4);
      assertThat(api.inbox(Map.of("severity", "ERROR")).get("total")).isEqualTo(2);
      Map<String, Object> page = api.inbox(Map.of("offset", "1", "limit", "2"));
      assertThat(page).containsEntry("total", 5).containsEntry("offset", 1).containsEntry("limit", 2);
      assertThat(items(page)).extracting(i -> i.get("title")).containsExactly("Job 3 failed", "Job 2 failed");

      assertThat(api.dismiss(Map.of("id", banners.get(0).get("id"))).get("updated")).isEqualTo(1);
      summary = api.inboxSummary();
      assertThat(summary.get("unreadCount")).isEqualTo(4);
      assertThat((List<?>) summary.get("banners")).isEmpty();
      assertThat(api.markRead(Map.of("all", true)).get("updated")).isEqualTo(4);
      assertThat(api.inbox(Map.of("status", "unread")).get("total")).isEqualTo(0);
      assertThat(api.dismiss(Map.of("all", true)).get("updated")).isEqualTo(4);
      assertThat(api.inbox(Map.of()).get("total")).isEqualTo(0);
      assertThatThrownBy(() -> api.dismiss(Map.of())).hasMessageContaining("Send id");
      assertThatThrownBy(() -> api.dismiss(Map.of("id", "x"))).hasMessageContaining("numbers");
   }



   /*******************************************************************************
    ** Producers: a repeated sourceKey updates one notification and brings it
    ** back to unread; links are made safe; bad signals are refused.
    *******************************************************************************/
   @Test
   void testProducerDedupAndLinks() throws Exception
   {
      NotificationSignal signal = new NotificationSignal("Export failed").withSourceKey("process:abc").withCategory(NotificationCategory.JOB)
         .withLink(AlertLink.toRecord("widget", 5, "Widget")).withAudience(NotificationAudience.user("viewer-1"));
      Integer first = runtime.getNotifications().notify(signal);
      loginAs("viewer-1");
      AlertsApi api = new AlertsApi(runtime);
      api.markRead(Map.of("all", true));
      assertThat(api.inboxSummary().get("unreadCount")).isEqualTo(0);

      Integer second = runtime.getNotifications().notify(signal.withTitle("Export failed again"));
      assertThat(second).isEqualTo(first);
      Map<String, Object> summary = api.inboxSummary();
      assertThat(summary.get("unreadCount")).isEqualTo(1);
      assertThat(items(summary).get(0)).containsEntry("title", "Export failed again").containsEntry("href", "/app/widget/5");

      assertThat(NotificationService.linkUrl(AlertLink.toUrl("javascript:alert(1)", "x"))).isNull();
      assertThat(NotificationService.linkUrl(AlertLink.toUrl("https://example.com/x", "x"))).isEqualTo("https://example.com/x");
      assertThat(NotificationService.linkUrl(AlertLink.toPath("/app/alerts", "x"))).isEqualTo("/app/alerts");
      assertThat(NotificationService.safeLink("//evil.example.com")).isNull();
      for(String bad : List.of("/\\evil.com", "\\\\evil.com", "javascript:alert(1)", "data:text/html,x", "http://x.example.com", "https:///nohost", "/a b", "/a	b", "https://"))
      {
         assertThat(NotificationService.safeLink(bad)).as(bad).isNull();
      }
      assertThat(NotificationService.safeLink(" https://example.com/x ")).isEqualTo("https://example.com/x");
      assertThat(NotificationService.safeLink("/")).isEqualTo("/");
      assertThatThrownBy(() -> runtime.getNotifications().notify(new NotificationSignal("No audience"))).hasMessageContaining("audience");

      ////////////////////////////////////////////////////////////////////
      // through the sink (the producer SPI path)                        //
      ////////////////////////////////////////////////////////////////////
      runtime.sink().notify(new NotificationSignal("Shared with you: Q3 notebook").withCategory(NotificationCategory.SHARE).withAudience(NotificationAudience.user("viewer-1")));
      assertThat(api.inboxSummary().get("unreadCount")).isEqualTo(2);
   }



   /*******************************************************************************
    ** Preserve the method descriptors used by released notification producers,
    ** including the local AlertLink, and deliver to the selected user's bell.
    *******************************************************************************/
   @Test
   void testLegacyNotificationSinkSignaturesAndDelivery() throws Exception
   {
      assertThat(AlertsRuntime.class.getMethod("sink").getReturnType()).isEqualTo(QbitAlertsSink.class);
      assertThat(QbitAlertsSink.class.getMethod("notify", NotificationSignal.class).getReturnType()).isEqualTo(void.class);
      assertThat(NotificationSignal.class.getMethod("withLink", AlertLink.class).getReturnType()).isEqualTo(NotificationSignal.class);
      assertThat(NotificationSignal.class.getMethod("getLink").getReturnType()).isEqualTo(AlertLink.class);
      assertThat(NotificationSignal.class.getMethod("withAudience", NotificationAudience.class).getReturnType()).isEqualTo(NotificationSignal.class);
      assertThat(AlertLink.class.getMethod("toRecord", String.class, Serializable.class, String.class).getReturnType()).isEqualTo(AlertLink.class);
      assertThat(NotificationAudience.class.getMethod("user", String.class).getReturnType()).isEqualTo(NotificationAudience.class);

      runtime.sink().notify(new NotificationSignal("Shipment delivered")
         .withSeverity(NotificationSeverity.INFO).withCategory(NotificationCategory.JOB)
         .withLink(AlertLink.toRecord("widget", 5, "Shipment"))
         .withAudience(NotificationAudience.user("viewer-1")));

      loginAs("viewer-1");
      Map<String, Object> summary = new AlertsApi(runtime).inboxSummary();
      assertThat(summary.get("unreadCount")).isEqualTo(1);
      assertThat(items(summary).get(0)).containsEntry("title", "Shipment delivered").containsEntry("href", "/app/widget/5");
      loginAs("viewer-2");
      assertThat(new AlertsApi(runtime).inboxSummary().get("unreadCount")).isEqualTo(0);
   }



   /*******************************************************************************
    ** Audiences: roles from a directory, permission holders, warnings.
    *******************************************************************************/
   @Test
   void testAudiences() throws Exception
   {
      knownUsers("viewer-1");
      NotificationService service = runtime.getNotifications();
      QRecord roles = new QRecord().withValue("audienceType", "ROLES").withValue("audienceValue", "editors");
      assertThat(service.preview(roles).userIds()).isEmpty();
      assertThat(service.preview(roles).warnings()).anyMatch(w -> w.contains("no roles"));

      config.setUserDirectory(new QCodeReference(TestDirectory.class));
      assertThat(service.preview(roles).userIds()).containsExactly("viewer-2");
      assertThat(service.preview(new QRecord().withValue("audienceType", "ROLES").withValue("audienceValue", "STAFF, admins")).userIds())
         .containsExactlyInAnyOrder("viewer-1", "viewer-2", "admin-1");
      assertThat(service.preview(new QRecord().withValue("audienceType", "PERMISSION").withValue("audienceValue", "alerts.admin")).userIds()).containsExactly("admin-1");
      assertThat(service.preview(new QRecord().withValue("audienceType", "USERS").withValue("audienceValue", "viewer-1\nnew-person")).labels())
         .containsEntry("viewer-1", "Vera Viewer").containsEntry("new-person", "new-person");
      assertThat(service.preview(new QRecord().withValue("audienceType", "nonsense")).warnings()).isNotEmpty();

      config.setMaxAudienceSize(2);
      AlertsAudienceResolver.Resolved capped = service.preview(new QRecord().withValue("audienceType", "ALL_USERS"));
      assertThat(capped.userIds()).hasSize(2);
      assertThat(capped.warnings()).anyMatch(w -> w.contains("maxAudienceSize"));
   }



   /*******************************************************************************
    ** Validation on the tables: links, audiences, security mutes.
    *******************************************************************************/
   @Test
   void testValidation() throws Exception
   {
      loginAs(AUTHOR);
      assertThat(author(new QRecord().withValue("title", "x").withValue("severity", "INFO").withValue("category", "SYSTEM").withValue("audienceType", "ALL_USERS")
         .withValue("linkUrl", "javascript:alert(1)")).getErrors()).anyMatch(e -> e.getMessage().contains("app path"));
      assertThat(author(new QRecord().withValue("title", "x").withValue("severity", "INFO").withValue("category", "SYSTEM").withValue("audienceType", "USERS"))
         .getErrors()).anyMatch(e -> e.getMessage().contains("user ids"));

      loginAs("viewer-1");
      QRecord muted = new InsertAction().execute(new InsertInput(AlertsNotificationPreference.TABLE_NAME).withRecord(new QRecord()
         .withValue("category", "SECURITY").withValue("inAppMuted", true))).getRecords().get(0);
      assertThat(muted.getErrors()).anyMatch(e -> e.getMessage().contains("can't be muted"));
      QRecord security = new InsertAction().execute(new InsertInput(AlertsNotificationPreference.TABLE_NAME).withRecord(new QRecord()
         .withValue("category", "SECURITY").withValue("emailMode", "IMMEDIATE"))).getRecords().get(0);
      assertThat(security.getErrors()).isEmpty();
      assertThat(AlertsDb.byId(AlertsNotificationPreference.TABLE_NAME, security.getValue("id")).getValueString("userId")).isEqualTo("viewer-1");
      QRecord update = new UpdateAction().execute(new UpdateInput(AlertsNotificationPreference.TABLE_NAME).withRecord(new QRecord()
         .withValue("id", security.getValue("id")).withValue("inAppMuted", true))).getRecords().get(0);
      assertThat(update.getErrors()).anyMatch(e -> e.getMessage().contains("can't be muted"));

      ////////////////////////////////////////////////////////////////////
      // a muted category skips the user; security is never skipped      //
      ////////////////////////////////////////////////////////////////////
      new InsertAction().execute(new InsertInput(AlertsNotificationPreference.TABLE_NAME).withRecord(new QRecord().withValue("category", "MARKETING").withValue("inAppMuted", true)));
      runtime.getNotifications().notify(new NotificationSignal("Big sale").withCategory(NotificationCategory.MARKETING).withAudience(NotificationAudience.users("viewer-1", "viewer-2")));
      runtime.getNotifications().notify(new NotificationSignal("Password changed").withCategory(NotificationCategory.SECURITY).withAudience(NotificationAudience.user("viewer-1")));
      assertThat(new AlertsApi(runtime).inboxSummary().get("unreadCount")).isEqualTo(1);
   }



   /*******************************************************************************
    ** Scheduling, expiry, purge, and deleting with recipients.
    *******************************************************************************/
   @Test
   void testScheduleExpireAndPurge() throws Exception
   {
      knownUsers("viewer-1");
      loginAs(AUTHOR);
      QRecord draft = author(new QRecord().withValue("title", "Maintenance tonight").withValue("severity", "WARNING").withValue("category", "SYSTEM")
         .withValue("audienceType", "USERS").withValue("audienceValue", "viewer-1"));
      Integer id = draft.getValueInteger("id");
      assertThat(String.join("\n", run(AlertsAction.SCHEDULE_NOTIFICATION, List.of(id)))).contains("future");

      new UpdateAction().execute(new UpdateInput(AlertsNotification.TABLE_NAME).withRecord(new QRecord().withValue("id", id)
         .withValue("publishAt", clock.instant().plus(Duration.ofHours(1))).withValue("expireAt", clock.instant().plus(Duration.ofHours(3)))));
      assertThat(String.join("\n", run(AlertsAction.SCHEDULE_NOTIFICATION, List.of(id)))).contains("notification was scheduled");
      assertThat(runtime.getNotifications().sendDue()).isEqualTo(0);
      clock.advance(Duration.ofMinutes(61));
      assertThat(runtime.getNotifications().sendDue()).isEqualTo(1);

      loginAs("viewer-1");
      AlertsApi api = new AlertsApi(runtime);
      assertThat(api.inboxSummary().get("unreadCount")).isEqualTo(1);
      clock.advance(Duration.ofHours(3));
      assertThat(api.inboxSummary().get("unreadCount")).isEqualTo(0);

      assertThat(runtime.getNotifications().purge()).isEqualTo(0);
      clock.advance(Duration.ofDays(8));
      assertThat(runtime.getNotifications().purge()).isEqualTo(1);
      assertThat(runtime.asSystem(() -> all(AlertsNotificationRecipient.TABLE_NAME))).isEmpty();

      ////////////////////////////////////////////////////////////////////
      // deleting a notification deletes its recipients                  //
      ////////////////////////////////////////////////////////////////////
      Integer other = runtime.getNotifications().notify(new NotificationSignal("Hello").withAudience(NotificationAudience.user("viewer-1")));
      loginAs(AUTHOR);
      new DeleteAction().execute(new DeleteInput(AlertsNotification.TABLE_NAME).withPrimaryKeys(List.of(other)));
      assertThat(runtime.asSystem(() -> all(AlertsNotificationRecipient.TABLE_NAME))).isEmpty();
   }
}
