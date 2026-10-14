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

package com.kingsrook.qbits.alerts.engine;


import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qbits.alerts.AlertsSecurity;
import com.kingsrook.qbits.alerts.BaseAlertsTest;
import com.kingsrook.qbits.alerts.model.AlertsDelivery;
import com.kingsrook.qbits.alerts.model.AlertsNotificationRecipient;
import com.kingsrook.qbits.alerts.model.AlertsSubscription;
import com.kingsrook.qbits.alerts.model.AlertsUserPreference;
import com.kingsrook.qqq.backend.core.actions.collaboration.RecordFollowersProviderInterface;
import com.kingsrook.qqq.backend.core.alerts.AlertRecordRef;
import com.kingsrook.qqq.backend.core.alerts.AlertSeverity;
import com.kingsrook.qqq.backend.core.alerts.AlertSignal;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.data.QRecord;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.collaboration.FollowEvent;
import com.kingsrook.qqq.backend.core.model.metadata.collaboration.RecordFeature;
import com.kingsrook.qqq.backend.core.model.metadata.collaboration.RecordFeaturesMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.collaboration.RecordFollower;
import com.kingsrook.qqq.backend.core.model.metadata.collaboration.RecordReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;


/*******************************************************************************
 ** Core followers enter the normal routing pipeline without a collaboration qbit.
 *******************************************************************************/
class FollowerFanoutTest extends BaseAlertsTest
{
   /*******************************************************************************
    ** Keep provider state isolated between tests; absence is the default.
    *******************************************************************************/
   @BeforeEach
   void resetProvider()
   {
      Provider.followers = List.of();
      Provider.references.clear();
      Provider.failure = false;
   }



   /*******************************************************************************
    ** Register the optional core SPI in instance metadata, with FOLLOW enabled.
    *******************************************************************************/
   private void followers(String... userIds)
   {
      Provider.followers = java.util.Arrays.stream(userIds).map(id -> new RecordFollower().withUserId(id).withNotifyOnChange(true)).toList();
      RecordFeaturesMetaData.ofOrWithNew(qInstance).withEnabled(RecordFeature.FOLLOW, true)
         .withFollowersProvider(new QCodeReference(Provider.class));
   }



   /*******************************************************************************
    ** A normal subscriber must retain their destination when also following.
    *******************************************************************************/
   private void subscribe(String userId) throws QException
   {
      runtime.asSystem(() -> AlertsDb.insert(AlertsSubscription.TABLE_NAME, new QRecord()
         .withValue("subscriberType", "USER").withValue("userId", userId)
         .withValue("scopeType", "ALL").withValue("minSeverity", "WARNING").withValue("enabled", true)));
   }



   /*******************************************************************************
    ** Use a real source row, without a subject-table visibility lock on the alert.
    *******************************************************************************/
   private void raise(Integer... widgetIds) throws QException
   {
      AlertSignal signal = new AlertSignal("test.followers").withTitle("Widget needs attention").withSeverity(AlertSeverity.ERROR)
         .withDedupKey(java.util.Arrays.toString(widgetIds));
      for(Integer widgetId : widgetIds)
      {
         signal.withRecordRef(AlertRecordRef.of(WIDGET, widgetId));
      }
      runtime.getEngine().raise(signal);
   }



   /*******************************************************************************
    ** Followers from multiple refs and an existing subscription receive only once.
    *******************************************************************************/
   @Test
   void followersJoinSubscribersOnce() throws Exception
   {
      List<Integer> widgets = insertWidgets("BROKEN", "BROKEN");
      followers(VIEWER, "viewer-2", VIEWER);
      subscribe(VIEWER);
      raise(widgets.toArray(Integer[]::new));

      assertThat(Provider.references).containsExactly(new RecordReference(WIDGET, widgets.get(0).toString()), new RecordReference(WIDGET, widgets.get(1).toString()));
      assertThat(all(AlertsNotificationRecipient.TABLE_NAME)).extracting(row -> row.getValueString("userId")).containsExactlyInAnyOrder(VIEWER, "viewer-2");
      assertThat(all(AlertsDelivery.TABLE_NAME)).hasSize(2).allSatisfy(row -> assertThat(row.getValueString("status")).isEqualTo("SENT"));
      assertThat(where(AlertsDelivery.TABLE_NAME, AlertsDb.eq("recipientUserId", VIEWER))).singleElement()
         .satisfies(row -> assertThat(row.getValue("subscriptionId")).isNotNull());
      assertThat(QContext.getQSession().getUser().getIdReference()).isEqualTo(ADMIN);
   }



   /*******************************************************************************
    ** Table access and a known recipient session are required, even for the inbox.
    *******************************************************************************/
   @Test
   void inaccessibleAndUnknownFollowersAreExcluded() throws Exception
   {
      followers(VIEWER, OUTSIDER, "unknown-user", "");
      raise(insertWidgets("BROKEN").get(0));
      assertThat(all(AlertsNotificationRecipient.TABLE_NAME)).extracting(row -> row.getValueString("userId")).containsExactly(VIEWER);
      assertThat(all(AlertsDelivery.TABLE_NAME)).extracting(row -> row.getValueString("recipientUserId")).containsExactly(VIEWER);
   }



   /*******************************************************************************
    ** Table permission alone is insufficient when the source has record locks.
    *******************************************************************************/
   @Test
   void sourceRecordLocksAreEnforced() throws Exception
   {
      Integer widget = insertWidgets("BROKEN").get(0);
      runtime.asSystem(() -> AlertsDb.update(WIDGET, new QRecord().withValue("id", widget).withValue("name", VIEWER)));
      qInstance.getTable(WIDGET).withRecordSecurityLock(AlertsSecurity.ownerLock("name"));
      followers(VIEWER, "viewer-2");
      raise(widget);
      assertThat(all(AlertsNotificationRecipient.TABLE_NAME)).extracting(row -> row.getValueString("userId")).containsExactly(VIEWER);
   }



   /*******************************************************************************
    ** No provider preserves subscription behavior, even with a source record.
    *******************************************************************************/
   @Test
   void noProviderPreservesSubscribers() throws Exception
   {
      subscribe(VIEWER);
      raise(insertWidgets("BROKEN").get(0));
      assertThat(Provider.references).isEmpty();
      assertThat(all(AlertsNotificationRecipient.TABLE_NAME)).extracting(row -> row.getValueString("userId")).containsExactly(VIEWER);
   }



   /*******************************************************************************
    ** Recordless alerts never ask for followers.
    *******************************************************************************/
   @Test
   void noRecordDoesNotFanOut() throws Exception
   {
      followers(VIEWER);
      raise();
      assertThat(Provider.references).isEmpty();
      assertThat(all(AlertsDelivery.TABLE_NAME)).isEmpty();
   }



   /*******************************************************************************
    ** A disabled core FOLLOW feature leaves existing recipients intact.
    *******************************************************************************/
   @Test
   void disabledFollowPreservesSubscribers() throws Exception
   {
      followers("viewer-2");
      subscribe(VIEWER);
      Integer widget = insertWidgets("BROKEN").get(0);
      RecordFeaturesMetaData.ofOrWithNew(qInstance.getTable(WIDGET)).withEnabled(RecordFeature.FOLLOW, false);
      raise(widget);
      assertThat(Provider.references).isEmpty();
      assertThat(all(AlertsNotificationRecipient.TABLE_NAME)).extracting(row -> row.getValueString("userId")).containsExactly(VIEWER);
   }



   /*******************************************************************************
    ** Core isolates provider failures so normal subscribers still receive the alert.
    *******************************************************************************/
   @Test
   void providerFailurePreservesSubscribers() throws Exception
   {
      followers("viewer-2");
      subscribe(VIEWER);
      Provider.failure = true;
      raise(insertWidgets("BROKEN").get(0));
      assertThat(Provider.references).hasSize(1);
      assertThat(all(AlertsNotificationRecipient.TABLE_NAME)).extracting(row -> row.getValueString("userId")).containsExactly(VIEWER);
   }



   /*******************************************************************************
    ** Followers use ordinary in-app/email preferences and vacation suppression.
    *******************************************************************************/
   @Test
   void followerPreferencesAndPauseApply() throws Exception
   {
      followers(VIEWER, "viewer-2", "viewer-3");
      runtime.asSystem(() ->
      {
         AlertsDb.insert(AlertsUserPreference.TABLE_NAME, new QRecord().withValue("userId", VIEWER).withValue("inAppEnabled", false).withValue("emailEnabled", false));
         AlertsDb.insert(AlertsUserPreference.TABLE_NAME, new QRecord().withValue("userId", "viewer-2").withValue("inAppEnabled", true)
            .withValue("emailEnabled", true).withValue("emailAddress", "follower@example.com").withValue("pausedUntil", clock.instant().plus(Duration.ofDays(1))));
         AlertsDb.insert(AlertsUserPreference.TABLE_NAME, new QRecord().withValue("userId", "viewer-3").withValue("inAppEnabled", false)
            .withValue("emailEnabled", true).withValue("emailAddress", "other@example.com"));
         return (null);
      });
      raise(insertWidgets("BROKEN").get(0));
      assertThat(all(AlertsNotificationRecipient.TABLE_NAME)).extracting(row -> row.getValueString("userId")).containsExactly("viewer-2");
      assertThat(all(AlertsDelivery.TABLE_NAME)).hasSize(3);
      assertThat(where(AlertsDelivery.TABLE_NAME, AlertsDb.eq("suppressedReason", "USER_PAUSED"))).singleElement()
         .satisfies(row -> assertThat(row.getValueString("recipientUserId")).isEqualTo("viewer-2"));
      assertThat(where(AlertsDelivery.TABLE_NAME, AlertsDb.eq("recipientUserId", "viewer-3"))).singleElement()
         .satisfies(row -> assertThat(row.getValueString("status")).isEqualTo("PENDING"));
   }



   /*******************************************************************************
    ** A core-only provider; the event preference decision belongs to the provider.
    *******************************************************************************/
   public static class Provider implements RecordFollowersProviderInterface
   {
      static List<RecordFollower> followers = List.of();
      static final List<RecordReference> references = new ArrayList<>();
      static boolean failure;



      /***************************************************************************
       ** Assert ALERT dispatch and record lookup context at the SPI boundary.
       ***************************************************************************/
      @Override
      public List<RecordFollower> getFollowers(QInstance instance, RecordReference reference, FollowEvent event) throws QException
      {
         assertThat(instance).isSameAs(QContext.getQInstance());
         assertThat(event).isEqualTo(FollowEvent.ALERT);
         references.add(reference);
         if(failure)
         {
            throw (new QException("Test provider unavailable"));
         }
         return (followers);
      }
   }
}
