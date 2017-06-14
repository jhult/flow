/*
 * Copyright 2000-2017 Vaadin Ltd.
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
package com.vaadin.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EventObject;
import java.util.List;
import java.util.Objects;

/**
 * Event fired to {@link VaadinServiceInitListener} when a {@link VaadinService}
 * is being initialized.
 * <p>
 * This event can also be used to add {@link RequestHandler}s that will be used
 * by the {@code VaadinService} for handling all requests.
 * <p>
 * {@link BootstrapListener}s can also be registered, that are used to modify
 * the initial HTML of the application.
 *
 * @author Vaadin Ltd
 */
public class ServiceInitEvent extends EventObject {

    private List<RequestHandler> addedRequestHandlers = new ArrayList<>();
    private List<BootstrapListener> addedBootstrapListeners = new ArrayList<>();

    /**
     * Creates a new service init event for a given {@link VaadinService} and
     * the {@link RequestHandler} that will be used by the service.
     *
     * @param service
     *            the Vaadin service of this request
     */
    public ServiceInitEvent(VaadinService service) {
        super(service);
    }

    /**
     * Adds a new request handler that will be used by this service. The added
     * handler will be run before any of the framework's own request handlers,
     * but the ordering relative to other custom handlers is not guaranteed.
     *
     * @param requestHandler
     *            the request handler to add, not <code>null</code>
     */
    public void addRequestHandler(RequestHandler requestHandler) {
        Objects.requireNonNull(requestHandler,
                "Request handler cannot be null");

        addedRequestHandlers.add(requestHandler);
    }

    /**
     * Adds a new bootstrap listener that will be used by this service. The
     * ordering of multiple added bootstrap listeners is not guaranteed.
     *
     * @param bootstrapListener
     *            the bootstrap listener to add, not <code>null</code>
     */
    public void addBootstrapListener(BootstrapListener bootstrapListener) {
        Objects.requireNonNull(bootstrapListener,
                "Bootstrap listener cannot be null");

        addedBootstrapListeners.add(bootstrapListener);
    }

    /**
     * Gets an unmodifiable list of all custom request handlers that have been
     * added for the service.
     *
     * @return the current list of added request handlers
     */
    public List<RequestHandler> getAddedRequestHandlers() {
        return Collections.unmodifiableList(addedRequestHandlers);
    }

    /**
     * Gets an unmodifiable list of all bootstrap listeners that have been added
     * for the service.
     *
     * @return the current list of added bootstrap listeners
     */
    public List<BootstrapListener> getAddedBootstrapListeners() {
        return Collections.unmodifiableList(addedBootstrapListeners);
    }

    @Override
    public VaadinService getSource() {
        return (VaadinService) super.getSource();
    }

}