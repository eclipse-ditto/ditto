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
package org.eclipse.ditto.model.amqpbridge;

import java.util.Map;
import java.util.Set;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;

/**
 * Factory to create new {@link AmqpConnection} instances.
 */
@Immutable
public final class AmqpBridgeModelFactory {

    private AmqpBridgeModelFactory() {
        throw new AssertionError();
    }

    /**
     * Returns a new {@code Connection}.
     *
     * @param id the connection identifier.
     * @param connectionType the connection type
     * @param uri the connection uri.
     * @param authorizationSubject the connection authorization subject.
     * @param sources the connection sources.
     * @param failoverEnabled whether failover is enabled for the connection or not.
     * @return the ImmutableConnection.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static AmqpConnection newConnection(final String id,
            final ConnectionType connectionType, final String uri,
            final AuthorizationSubject authorizationSubject, final Set<String> sources, final boolean failoverEnabled,
            final boolean validateCertificates, final int throttle) {
        return ImmutableAmqpConnection.of(id, connectionType, uri, authorizationSubject, sources, failoverEnabled,
                validateCertificates, throttle);
    }

    /**
     * Creates a new {@code Connection} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the Connection to be created.
     * @return a new Connection which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static AmqpConnection connectionFromJson(final JsonObject jsonObject) {
        return ImmutableAmqpConnection.fromJson(jsonObject);
    }

    /**
     * Returns a new {@code MappingContext}.
     *
     * @return
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static MappingContext newMappingContext(final String contentType, final String mappingEngine,
            final Map<String, String> options) {
        return ImmutableMappingContext.of(contentType, mappingEngine, options);
    }

    /**
     * Creates a new {@code MappingContext} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the MappingContext to be created.
     * @return a new MappingContext which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static MappingContext mappingContextFromJson(final JsonObject jsonObject) {
        return ImmutableMappingContext.fromJson(jsonObject);
    }

}
