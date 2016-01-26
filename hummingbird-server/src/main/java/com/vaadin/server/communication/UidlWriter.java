/*
 * Copyright 2000-2014 Vaadin Ltd.
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

package com.vaadin.server.communication;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.vaadin.annotations.JavaScript;
import com.vaadin.annotations.StyleSheet;
import com.vaadin.server.ClientConnector;
import com.vaadin.server.LegacyCommunicationManager;
import com.vaadin.server.LegacyCommunicationManager.ClientCache;
import com.vaadin.server.SystemMessages;
import com.vaadin.server.VaadinService;
import com.vaadin.server.VaadinSession;
import com.vaadin.shared.ApplicationConstants;
import com.vaadin.ui.Component;
import com.vaadin.ui.ConnectorTracker;
import com.vaadin.ui.HasComponents;
import com.vaadin.ui.UI;

import elemental.json.Json;
import elemental.json.JsonArray;
import elemental.json.JsonObject;
import elemental.json.JsonValue;

/**
 * Serializes pending server-side changes to UI state to JSON. This includes
 * shared state, client RPC invocations, connector hierarchy changes, connector
 * type information among others.
 *
 * @author Vaadin Ltd
 * @since 7.1
 */
public class UidlWriter implements Serializable {

    /**
     * Creates a JSON object containing all pending changes to the given UI.
     *
     * @param ui
     *            The {@link UI} whose changes to write
     * @param async
     *            True if this message is sent by the server asynchronously,
     *            false if it is a response to a client message.
     * @return JSON object containing the UIDL response
     */
    public JsonObject createUidl(UI ui, boolean async) {
        JsonObject response = Json.createObject();

        VaadinSession session = ui.getSession();
        VaadinService service = session.getService();

        // Purge pending access calls as they might produce additional changes
        // to write out
        service.runPendingAccessTasks(session);

        Set<ClientConnector> processedConnectors = new HashSet<ClientConnector>();

        LegacyCommunicationManager manager = session.getCommunicationManager();
        ClientCache clientCache = manager.getClientCache(ui);
        boolean repaintAll = clientCache.isEmpty();
        // Paints components
        ConnectorTracker uiConnectorTracker = ui.getConnectorTracker();
        getLogger().log(Level.FINE, "* Creating response to client");

        while (true) {
            ArrayList<ClientConnector> connectorsToProcess = new ArrayList<ClientConnector>();
            for (ClientConnector c : uiConnectorTracker.getDirtyConnectors()) {
                if (!processedConnectors.contains(c)
                        && LegacyCommunicationManager
                                .isConnectorVisibleToClient(c)) {
                    connectorsToProcess.add(c);
                }
            }

            if (connectorsToProcess.isEmpty()) {
                break;
            }

            for (ClientConnector connector : connectorsToProcess) {
                boolean initialized = uiConnectorTracker
                        .isClientSideInitialized(connector);
                processedConnectors.add(connector);

                try {
                    connector.beforeClientResponse(!initialized);
                } catch (RuntimeException e) {
                    manager.handleConnectorRelatedException(connector, e);
                }
            }
        }

        getLogger().log(Level.FINE, "Found " + processedConnectors.size()
                + " dirty connectors to paint");

        uiConnectorTracker.setWritingResponse(true);
        try {

            int syncId = service.getDeploymentConfiguration()
                    .isSyncIdCheckEnabled()
                            ? uiConnectorTracker.getCurrentSyncId() : -1;
            response.put(ApplicationConstants.SERVER_SYNC_ID, syncId);
            if (repaintAll) {
                response.put(ApplicationConstants.RESYNCHRONIZE_ID, true);
            }
            int nextClientToServerMessageId = ui
                    .getLastProcessedClientToServerId() + 1;
            response.put(ApplicationConstants.CLIENT_TO_SERVER_ID,
                    nextClientToServerMessageId);

            uiConnectorTracker.markAllConnectorsClean();

            SystemMessages messages = ui.getSession().getService()
                    .getSystemMessages(ui.getLocale(), null);
            // TODO hilightedConnector
            JsonObject meta = new MetadataWriter().createMetadata(ui,
                    repaintAll, async, messages);
            response.put("meta", meta);

            Set<Class<? extends Component>> usedComponents = new HashSet<Class<? extends Component>>();
            findClientConnectors(ui, usedComponents);

            List<Class<? extends ClientConnector>> newConnectorTypes = new ArrayList<Class<? extends ClientConnector>>();

            for (Class<? extends ClientConnector> class1 : usedComponents) {
                if (clientCache.cache(class1)) {
                    // client does not know the mapping key for this type, send
                    // mapping to client
                    newConnectorTypes.add(class1);
                }
            }

            // TODO Refactor to DependencyWriter or something
            /*
             * Ensure super classes come before sub classes to get script
             * dependency order right. Sub class @JavaScript might assume that
             *
             * @JavaScript defined by super class is already loaded.
             */
            Collections.sort(newConnectorTypes, new Comparator<Class<?>>() {
                @Override
                public int compare(Class<?> o1, Class<?> o2) {
                    // TODO optimize using Class.isAssignableFrom?
                    return hierarchyDepth(o1) - hierarchyDepth(o2);
                }

                private int hierarchyDepth(Class<?> type) {
                    if (type == Object.class) {
                        return 0;
                    } else {
                        return hierarchyDepth(type.getSuperclass()) + 1;
                    }
                }
            });

            List<String> scriptDependencies = new ArrayList<String>();
            List<String> styleDependencies = new ArrayList<String>();

            for (Class<? extends ClientConnector> class1 : newConnectorTypes) {
                JavaScript jsAnnotation = class1
                        .getAnnotation(JavaScript.class);
                if (jsAnnotation != null) {
                    for (String uri : jsAnnotation.value()) {
                        scriptDependencies
                                .add(manager.registerDependency(uri, class1));
                    }
                }

                StyleSheet styleAnnotation = class1
                        .getAnnotation(StyleSheet.class);
                if (styleAnnotation != null) {
                    for (String uri : styleAnnotation.value()) {
                        styleDependencies
                                .add(manager.registerDependency(uri, class1));
                    }
                }
            }

            // Include script dependencies in output if there are any
            if (!scriptDependencies.isEmpty()) {
                response.put("scriptDependencies",
                        toJsonArray(scriptDependencies));
            }

            // Include style dependencies in output if there are any
            if (!styleDependencies.isEmpty()) {
                response.put("styleDependencies",
                        toJsonArray(styleDependencies));
            }

            for (ClientConnector connector : processedConnectors) {
                uiConnectorTracker.markClientSideInitialized(connector);
            }

            assert (uiConnectorTracker.getDirtyConnectors()
                    .isEmpty()) : "Connectors have been marked as dirty during the end of the paint phase. This is most certainly not intended.";

            response.put("timings", createPerformanceData(ui));

            return response;
        } finally {
            uiConnectorTracker.setWritingResponse(false);
            uiConnectorTracker.cleanConnectorMap();
        }
    }

    private void findClientConnectors(Component c,
            Set<Class<? extends Component>> classes) {
        classes.add(c.getClass());
        if (c instanceof HasComponents) {
            for (Component child : ((HasComponents) c)) {
                findClientConnectors(child, classes);
            }
        }

    }

    private JsonArray toJsonArray(List<String> list) {
        JsonArray result = Json.createArray();
        for (int i = 0; i < list.size(); i++) {
            result.set(i, list.get(i));
        }

        return result;
    }

    /**
     * Adds the performance timing data (used by TestBench 3) to the UIDL
     * response.
     */
    private JsonValue createPerformanceData(UI ui) {
        JsonArray timings = Json.createArray();
        timings.set(0, ui.getSession().getCumulativeRequestDuration());
        timings.set(1, ui.getSession().getLastRequestDuration());
        return timings;
    }

    private static final Logger getLogger() {
        return Logger.getLogger(UidlWriter.class.getName());
    }
}
