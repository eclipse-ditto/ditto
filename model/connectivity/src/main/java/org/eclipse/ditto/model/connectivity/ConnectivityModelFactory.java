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
package org.eclipse.ditto.model.connectivity;

import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;

/**
 * Factory to create new {@link Connection} instances.
 */
@Immutable
public final class ConnectivityModelFactory {

    private ConnectivityModelFactory() {
        throw new AssertionError();
    }

    /**
     * Returns a new {@code ConnectionBuilder} with the required fields set.
     *
     * @param id the connection identifier.
     * @param connectionType the connection type
     * @param uri the connection uri.
     * @param authorizationContext the connection authorization context.
     * @return the ConnectionBuilder.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ConnectionBuilder newConnectionBuilder(final String id,
            final ConnectionType connectionType, final String uri,
            final AuthorizationContext authorizationContext) {
        return ImmutableConnectionBuilder.of(id, connectionType, uri, authorizationContext);
    }

    /**
     * Creates a new {@code Connection} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the Connection to be created.
     * @return a new Connection which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static Connection connectionFromJson(final JsonObject jsonObject) {
        return ImmutableConnection.fromJson(jsonObject);
    }

    /**
     * Creates a new {@code ConnectionMetrics} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the Connection to be created.
     * @return a new ConnectionMetrics which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static ConnectionMetrics connectionMetricsFromJson(final JsonObject jsonObject) {
        return ImmutableConnectionMetrics.fromJson(jsonObject);
    }

    /**
     * Retruens a new {@code ConnectionMetrics}.
     *
     * @param connectionStatus the ConnectionStatus of the metrics to create
     * @param connectionStatusDetails the optional details about the connection status
     * @return a new ConnectionMetrics which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code connectionStatus} is {@code null}.
     */
    public static ConnectionMetrics newConnectionMetrics(final ConnectionStatus connectionStatus,
            final @Nullable String connectionStatusDetails) {
        return ImmutableConnectionMetrics.of(connectionStatus, connectionStatusDetails);
    }


    /**
     * Returns a new {@code MappingContext}.
     *
     * @return the created MappingContext.
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

    /**
     * Creates a new ExternalMessageBuilder initialized with the passed {@code headers}.
     *
     * @param headers the headers to initialize the builder with.
     * @return the builder.
     */
    public static ExternalMessageBuilder newExternalMessageBuilder(final Map<String, String> headers) {
        return new MutableExternalMessageBuilder(headers);
    }

    /**
     * Creates a new ExternalMessageBuilder for the passed {@code messageType} initialized with the passed
     * {@code headers}.
     *
     * @param headers the headers to initialize the builder with.
     * @param topicPath the topicPath to initialize the builder with.
     * @return the builder.
     */
    public static ExternalMessageBuilder newExternalMessageBuilder(final Map<String, String> headers,
            final String topicPath) {
        return new MutableExternalMessageBuilder(headers, topicPath);
    }

    /**
     * Creates a new ExternalMessageBuilder based on the passed existing {@code externalMessage}.
     *
     * @param externalMessage the ExternalMessage initialize the builder with.
     * @return the builder.
     */
    public static ExternalMessageBuilder newExternalMessageBuilder(final ExternalMessage externalMessage) {
        return new MutableExternalMessageBuilder(externalMessage);
    }

    public static Source newSource(final Set<String> sources, final int consumerCount) {
        return ImmutableSource.of(sources, consumerCount);
    }

    public static Source newSource(final int consumerCount, final String... sources) {
        return ImmutableSource.of(consumerCount, sources);
    }

    public static Target newTarget(final String target, final Set<String> topics) {
        return ImmutableTarget.of(target, topics);
    }

    public static Target newTarget(final String target, final String requiredTopic, final String... topics) {
        return ImmutableTarget.of(target, requiredTopic, topics);
    }

}
