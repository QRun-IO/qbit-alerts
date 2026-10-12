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

package com.kingsrook.qbits.alerts.home;


import java.io.Serializable;
import java.util.Map;
import com.kingsrook.qbits.alerts.AlertsPreferences;
import com.kingsrook.qbits.alerts.AlertsRuntime;
import com.kingsrook.qbits.alerts.AlertsSecurity;
import com.kingsrook.qqq.backend.core.actions.dashboard.widgets.AbstractWidgetRenderer;
import com.kingsrook.qqq.backend.core.context.QContext;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetInput;
import com.kingsrook.qqq.backend.core.model.actions.widgets.RenderWidgetOutput;
import com.kingsrook.qqq.backend.core.model.metadata.dashboard.QWidgetMetaData;


/*******************************************************************************
 ** Renders the Alerts home widgets. Which one is in the widget's "kind"
 ** default value. Data is read in the viewer's session (with the alerts
 ** record-security keys applied), so permissions and locks decide what shows.
 ** The first visit also sets up the viewer's settings (and, for admins, the
 ** bootstrap subscription).
 *******************************************************************************/
public class AlertsHomeWidgetRenderer extends AbstractWidgetRenderer
{
   public static final String KIND = "kind";



   /*******************************************************************************
    **
    *******************************************************************************/
   @Override
   public RenderWidgetOutput render(RenderWidgetInput input) throws QException
   {
      AlertsRuntime runtime = AlertsRuntime.get();
      AlertsSecurity.ensureSessionKeys(runtime.getQInstance(), QContext.getQSession());
      AlertsPreferences.ensure(runtime, QContext.getQSession());
      runtime.getTypeSync().syncIfNeeded();

      String kind = null;
      if(input.getWidgetMetaData() instanceof QWidgetMetaData widget)
      {
         Map<String, Serializable> defaults = widget.getDefaultValues();
         kind = defaults == null || defaults.get(KIND) == null ? null : String.valueOf(defaults.get(KIND));
      }
      return (new RenderWidgetOutput(AlertsHome.load(kind, runtime)));
   }
}
