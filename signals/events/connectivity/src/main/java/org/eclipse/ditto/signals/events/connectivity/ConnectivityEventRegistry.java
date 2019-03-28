/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
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
     * @param overwritingParseStrategies specifies parse strategies which should overwrite the default ones.
     * @return the event registry.
     */
    public static ConnectivityEventRegistry newInstance(
            final Map<String, JsonParsable<ConnectivityEvent>> overwritingParseStrategies) {
        final Map<String, JsonParsable<ConnectivityEvent>> parseStrategies = new HashMap<>();
        createParseStrategies(parseStrategies);
        parseStrategies.putAll(overwritingParseStrategies);
        return new ConnectivityEventRegistry(parseStrategies);
    }

    /**
     * Returns a new {@code ConnectivityEventRegistry}.
     *
     * @return the event registry.
     */
    public static ConnectivityEventRegistry newInstance() {
        final Map<String, JsonParsable<ConnectivityEvent>> parseStrategies = new HashMap<>();
        createParseStrategies(parseStrategies);
        return new ConnectivityEventRegistry(parseStrategies);
    }

    private static void createParseStrategies(final Map<String, JsonParsable<ConnectivityEvent>> parseStrategies) {
        parseStrategies.put(ConnectionCreated.TYPE, ConnectionCreated::fromJson);
        parseStrategies.put(ConnectionModified.TYPE, ConnectionModified::fromJson);
        parseStrategies.put(ConnectionOpened.TYPE, ConnectionOpened::fromJson);
        parseStrategies.put(ConnectionClosed.TYPE, ConnectionClosed::fromJson);
        parseStrategies.put(ConnectionDeleted.TYPE, ConnectionDeleted::fromJson);
    }

}
