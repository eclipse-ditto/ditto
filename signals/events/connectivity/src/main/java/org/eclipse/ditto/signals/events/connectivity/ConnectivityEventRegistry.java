/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.signals.events.connectivity;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.signals.base.JsonParsable;
import org.eclipse.ditto.signals.events.base.AbstractEventRegistry;

/**
 * An {@link org.eclipse.ditto.signals.events.base.EventRegistry} aware of all {@link ConnectivityEvent}s.
 */
@Immutable
public final class ConnectivityEventRegistry extends AbstractEventRegistry<ConnectivityEvent> {

    private ConnectivityEventRegistry(final Map<String, JsonParsable<ConnectivityEvent>> parseStrategies) {
        super(parseStrategies);
    }

    /**
     * Returns a new {@code ConnectivityEventRegistry}.
     *
     * @return the event registry.
     */
    public static ConnectivityEventRegistry newInstance() {
        final Map<String, JsonParsable<ConnectivityEvent>> parseStrategies = new HashMap<>();

        parseStrategies.put(ConnectionCreated.TYPE, ConnectionCreated::fromJson);
        parseStrategies.put(ConnectionModified.TYPE, ConnectionModified::fromJson);
        parseStrategies.put(ConnectionOpened.TYPE, ConnectionOpened::fromJson);
        parseStrategies.put(ConnectionClosed.TYPE, ConnectionClosed::fromJson);
        parseStrategies.put(ConnectionDeleted.TYPE, ConnectionDeleted::fromJson);

        return new ConnectivityEventRegistry(parseStrategies);
    }

}
