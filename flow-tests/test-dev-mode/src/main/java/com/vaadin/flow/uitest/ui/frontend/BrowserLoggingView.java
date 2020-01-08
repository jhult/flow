/*
 * Copyright 2000-2020 Vaadin Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.vaadin.flow.uitest.ui.frontend;

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Label;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.ui.LoadMode;
import com.vaadin.flow.uitest.servlet.ViewTestLayout;

@Route(value = "com.vaadin.flow.uitest.ui.frontend.BrowserLoggingView", layout = ViewTestLayout.class)
@Tag("div")
// context://frontend/ instead of frontend:// to have the same URL in production
@JavaScript(value = "context://frontend/com/vaadin/flow/uitest/ui/frontend/consoleLoggingProxy.js", loadMode = LoadMode.INLINE)
public class BrowserLoggingView extends Div {
    public BrowserLoggingView() {
        Label label = new Label("Just a label");
        label.setId("elementId");
        add(label);
    }
}
