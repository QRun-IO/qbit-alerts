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


import java.util.ArrayList;
import java.util.List;
import com.kingsrook.qbits.alerts.delivery.DeliveryWorker;
import com.kingsrook.qbits.alerts.engine.AlertsProcessListener;
import com.kingsrook.qbits.alerts.engine.AlertsSinkAdapter;
import com.kingsrook.qbits.alerts.engine.MonitorEvaluator;
import com.kingsrook.qbits.alerts.engine.QbitAlertsSink;
import com.kingsrook.qbits.alerts.metadata.AlertsHelp;
import com.kingsrook.qbits.alerts.metadata.AlertsTableMetaDataHelper;
import com.kingsrook.qbits.alerts.model.AlertsChannel;
import com.kingsrook.qbits.alerts.model.AlertsMonitor;
import com.kingsrook.qqq.backend.core.alerts.AlertSeverity;
import com.kingsrook.qqq.backend.core.alerts.AlertsInstanceMetaData;
import com.kingsrook.qqq.backend.core.alerts.DetailLevel;
import com.kingsrook.qqq.backend.core.alerts.QAlertTypeMetaData;
import com.kingsrook.qqq.backend.core.alerts.ResolveMode;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.model.metadata.MetaDataProducerMultiOutput;
import com.kingsrook.qqq.backend.core.model.metadata.QInstance;
import com.kingsrook.qqq.backend.core.model.metadata.code.QCodeReference;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitMetaData;
import com.kingsrook.qqq.backend.core.model.metadata.qbits.QBitMetaDataProducer;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Entry point of the Alerts QBit, a QQQ 4.1 QBitMetaDataProducer.
 **
 ** produce() validates the config, runs the component producers in the
 ** metadata package (tables, joins, possible values, security keys, record
 ** actions, scheduled processes, home widgets, the Alerts app) and then
 ** postProduceActions: declares the qbit's own alert types, builds the
 ** runtime (engine, router, delivery, monitors, sink), registers the process
 ** listener that feeds PROCESS_FAILED and process heartbeat monitors, and,
 ** when qqq-middleware-javalin is present, the /qqq/v1/alerts routes.
 **
 ** Host usage:
 **   new AlertsQBitProducer().withQBitConfig(new AlertsQBitConfig()
 **      .withBackendName("rdbms").withSchedulerName("quartz")
 **      .withMessagingProviderName("smtp").withFromAddress("alerts@example.com")
 **      .withPublicBaseUrl("https://admin.example.com")).produce(qInstance);
 *******************************************************************************/
public class AlertsQBitProducer implements QBitMetaDataProducer<AlertsQBitConfig>
{
   private static final QLogger LOG = QLogger.getLogger(AlertsQBitProducer.class);

   public static final String GROUP_ID    = "com.kingsrook.qbits";
   public static final String ARTIFACT_ID = "qbit-alerts";
   public static final String VERSION     = "0.1.0";

   private static final String JAVALIN_CLASS = "com.kingsrook.qqq.middleware.javalin.QJavalinMetaData";

   private AlertsQBitConfig qBitConfig;
   private String           namespace;



   /*******************************************************************************
    ** Fluent setter for the config.
    *******************************************************************************/
   public AlertsQBitProducer withQBitConfig(AlertsQBitConfig config)
   {
      this.qBitConfig = config;
      return (this);
   }



   /*******************************************************************************
    ** Getter for the config.
    *******************************************************************************/
   @Override
   public AlertsQBitConfig getQBitConfig()
   {
      return (qBitConfig);
   }



   /*******************************************************************************
    ** Fluent setter for the namespace.
    *******************************************************************************/
   public AlertsQBitProducer withNamespace(String namespace)
   {
      this.namespace = namespace;
      return (this);
   }



   /*******************************************************************************
    ** Getter for the namespace.
    *******************************************************************************/
   @Override
   public String getNamespace()
   {
      return (namespace);
   }



   /*******************************************************************************
    ** The qbit's identity.
    *******************************************************************************/
   @Override
   public QBitMetaData getQBitMetaData()
   {
      return (new QBitMetaData().withGroupId(GROUP_ID).withArtifactId(ARTIFACT_ID).withVersion(VERSION).withNamespace(namespace).withConfig(qBitConfig));
   }



   /*******************************************************************************
    ** Component producers live in the metadata package only (the api package
    ** needs qqq-middleware-javalin, which is optional).
    *******************************************************************************/
   @Override
   public String getPackageNameForFindingMetaDataProducers()
   {
      return (AlertsTableMetaDataHelper.class.getPackageName());
   }



   /*******************************************************************************
    ** Producers found by package scanning have no config: skip them.
    *******************************************************************************/
   @Override
   public boolean isEnabled()
   {
      return (qBitConfig != null);
   }



   /*******************************************************************************
    ** Produce and add everything to the instance. Returns an empty output so a
    ** host that also calls addSelfToInstance on the result adds nothing twice.
    *******************************************************************************/
   @Override
   public MetaDataProducerMultiOutput produce(QInstance qInstance) throws QException
   {
      if(qBitConfig == null)
      {
         throw (new QException("Alerts QBit configuration validation failed: no config was supplied (call withQBitConfig)"));
      }

      List<String> errors = new ArrayList<>();
      qBitConfig.validate(qInstance, errors);
      if(!errors.isEmpty())
      {
         throw (new QException("Alerts QBit configuration validation failed: " + String.join("; ", errors)));
      }

      MetaDataProducerMultiOutput output = QBitMetaDataProducer.super.produce(qInstance);
      output.addSelfToInstance(qInstance);
      AlertsHelp.apply(qInstance);
      return (new MetaDataProducerMultiOutput());
   }



   /*******************************************************************************
    ** Declare the qbit's own types, build the runtime, register the process
    ** listener and the routes.
    *******************************************************************************/
   @Override
   public void postProduceActions(MetaDataProducerMultiOutput output, QInstance qInstance) throws QException
   {
      AlertsInstanceMetaData alertsMetaData = AlertsInstanceMetaData.ofOrWithNew(qInstance);
      for(QAlertTypeMetaData type : ownAlertTypes())
      {
         if(alertsMetaData.getAlertType(type.getName()) == null)
         {
            alertsMetaData.withAlertType(type);
         }
      }

      AlertsRuntime previous = qBitConfig.getRuntime();
      if(previous != null)
      {
         previous.getSink().stop();
      }
      AlertsRuntime runtime = new AlertsRuntime(qBitConfig, qInstance);
      qBitConfig.setRuntime(runtime);
      alertsMetaData.withSink(new QCodeReference(AlertsSinkAdapter.class));

      String listener = AlertsProcessListener.class.getName();
      if(qInstance.getProcessLifecycleListeners() == null || qInstance.getProcessLifecycleListeners().stream().noneMatch(ref -> ref != null && listener.equals(ref.getName())))
      {
         qInstance.withProcessLifecycleListener(new QCodeReference(AlertsProcessListener.class));
      }

      boolean routes = false;
      if(Boolean.TRUE.equals(qBitConfig.getRegisterRoutes()) && isJavalinAvailable())
      {
         com.kingsrook.qbits.alerts.api.AlertsUiApi.register(qInstance);
         com.kingsrook.qbits.alerts.api.AlertsRouteProvider.register(qInstance);
         routes = true;
      }
      LOG.info("Alerts QBit produced", logPair("version", VERSION), logPair("backendName", qBitConfig.getBackendName()), logPair("scheduler", qBitConfig.getSchedulerName()),
         logPair("routes", routes));
   }



   /*******************************************************************************
    ** The alert types the qbit raises about itself.
    *******************************************************************************/
   public static List<QAlertTypeMetaData> ownAlertTypes()
   {
      return (List.of(
         new QAlertTypeMetaData()
            .withName(MonitorEvaluator.MONITOR_FAILING_TYPE)
            .withLabel("Monitor cannot run")
            .withSourceName(ARTIFACT_ID)
            .withGroupName("alerts")
            .withDefaultSeverity(AlertSeverity.ERROR)
            .withVisibilityPermission(AlertsQBitConfig.PERMISSION_ADMIN)
            .withSubjectTableName(AlertsMonitor.TABLE_NAME)
            .withResolveMode(ResolveMode.PRODUCER)
            .withDescription("A monitor's check failed three times in a row, so the condition it watches is not being checked.")
            .withHowToFix("Open the monitor, read Last Error, fix its definition (or what it checks), and choose Run check now."),
         new QAlertTypeMetaData()
            .withName(DeliveryWorker.CHANNEL_FAILING_TYPE)
            .withLabel("Alert channel failing")
            .withSourceName(ARTIFACT_ID)
            .withGroupName("alerts")
            .withDefaultSeverity(AlertSeverity.ERROR)
            .withVisibilityPermission(AlertsQBitConfig.PERMISSION_ADMIN)
            .withSubjectTableName(AlertsChannel.TABLE_NAME)
            .withResolveMode(ResolveMode.PRODUCER)
            .withExternalDetailCeiling(DetailLevel.MINIMAL)
            .withDescription("Three deliveries in a row through a channel failed. This alert is only ever shown in-app, so a broken channel can't loop.")
            .withHowToFix("Open the channel, check its settings and secret reference, then choose Send test notification and Retry delivery on the failed notifications."),
         new QAlertTypeMetaData()
            .withName(QbitAlertsSink.OVERFLOW_TYPE)
            .withLabel("Alert signals dropped")
            .withSourceName(ARTIFACT_ID)
            .withGroupName("alerts")
            .withDefaultSeverity(AlertSeverity.ERROR)
            .withVisibilityPermission(AlertsQBitConfig.PERMISSION_ADMIN)
            .withResolveMode(ResolveMode.MANUAL)
            .withDescription("The in-memory queue of producer signals was full, so some signals were not recorded.")
            .withHowToFix("Find the producer that floods (see Noisiest Alert Types), and check the database is keeping up. Resolve this alert when it has stopped."),
         new QAlertTypeMetaData()
            .withName(QbitAlertsSink.FLOOD_TYPE)
            .withLabel("Producer flooding alerts")
            .withSourceName(ARTIFACT_ID)
            .withGroupName("alerts")
            .withDefaultSeverity(AlertSeverity.WARNING)
            .withVisibilityPermission(AlertsQBitConfig.PERMISSION_ADMIN)
            .withQuietPeriod(java.time.Duration.ofHours(1))
            .withDescription("More than 1,000 signals in a minute arrived for one alert type; the rest were counted, not stored.")
            .withHowToFix("Fix the producer to raise once per condition (with a stable dedup key) instead of once per event.")));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static boolean isJavalinAvailable()
   {
      try
      {
         Class.forName(JAVALIN_CLASS, false, AlertsQBitProducer.class.getClassLoader());
         return (true);
      }
      catch(ClassNotFoundException | LinkageError e)
      {
         return (false);
      }
   }
}
