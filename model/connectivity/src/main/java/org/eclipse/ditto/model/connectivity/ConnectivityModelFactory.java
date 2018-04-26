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

import java.time.Instant;
import java.util.List;
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
     * @param connectionType the connection type.
     * @param connectionStatus the connection status.
     * @param uri the connection uri.
     * @param authorizationContext the connection authorization context.
     * @return the ConnectionBuilder.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ConnectionBuilder newConnectionBuilder(final String id,
            final ConnectionType connectionType, final ConnectionStatus connectionStatus, final String uri,
            final AuthorizationContext authorizationContext) {
        return ImmutableConnectionBuilder.of(id, connectionType, connectionStatus, uri, authorizationContext);
    }

    /**
     * Returns a mutable builder with a fluent API for an immutable {@link Connection}. The builder is initialised with the
     * values of the given Connection.
     *
     * @param connection the Connection which provides the initial values of the builder.
     * @return the new builder.
     * @throws NullPointerException if {@code connection} is {@code null}.
     */
    public static ConnectionBuilder newConnectionBuilder(final Connection connection) {
        return ImmutableConnectionBuilder.of(connection);
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
     * Returns a new {@code ConnectionMetrics}.
     *
     * @param connectionStatus the ConnectionStatus of the metrics to create
     * @param connectionStatusDetails the optional details about the connection status
     * @param inConnectionStatusSince the instant since when the Client is in its current ConnectionStatus
     * @param clientState the current state of the Client performing the connection
     * @param sourcesMetrics the metrics of all sources of the Connection
     * @param targetsMetrics the metrics of all targets of the Connection
     * @return a new ConnectionMetrics which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code connectionStatus} is {@code null}.
     */
    public static ConnectionMetrics newConnectionMetrics(final ConnectionStatus connectionStatus,
            final @Nullable String connectionStatusDetails, final Instant inConnectionStatusSince,
            final String clientState,
            final List<SourceMetrics> sourcesMetrics, final List<TargetMetrics> targetsMetrics) {
        return ImmutableConnectionMetrics.of(connectionStatus, connectionStatusDetails, inConnectionStatusSince,
                clientState, sourcesMetrics, targetsMetrics);
    }

    /**
     * Returns a new {@code SourceMetrics}.
     *
     * @param addressMetrics the AddressMetrics of all addresses in the source
     * @param consumedMessages the amount of consumed messages
     * @return a new SourceMetrics which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code connectionStatus} is {@code null}.
     */
    public static SourceMetrics newSourceMetrics(final Map<String, AddressMetric> addressMetrics,
            final long consumedMessages) {
        return ImmutableSourceMetrics.of(addressMetrics, consumedMessages);
    }

    /**
     * Creates a new {@code SourceMetrics} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the Connection to be created.
     * @return a new SourceMetrics which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static SourceMetrics sourceMetricsFromJson(final JsonObject jsonObject) {
        return ImmutableSourceMetrics.fromJson(jsonObject);
    }

    /**
     * Returns a new {@code TargetMetrics}.
     *
     * @param addressMetrics the AddressMetrics of all addresses in the target
     * @param publishedMessages the amount of published messages
     * @return a new SourceMetrics which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code connectionStatus} is {@code null}.
     */
    public static TargetMetrics newTargetMetrics(final Map<String, AddressMetric> addressMetrics,
            final long publishedMessages) {
        return ImmutableTargetMetrics.of(addressMetrics, publishedMessages);
    }

    /**
     * Creates a new {@code TargetMetrics} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the Connection to be created.
     * @return a new TargetMetrics which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static TargetMetrics targetMetricsFromJson(final JsonObject jsonObject) {
        return ImmutableTargetMetrics.fromJson(jsonObject);
    }

    /**
     * Returns a new {@code AddressMetric}.
     *
     * @param status the ConnectionStatus of the source metrics to create
     * @param statusDetails the optional details about the connection status
     * @param messageCount the amount of totally consumed/published messages
     * @param lastMessageAt the timestamp when the last message was consumed/published
     * @return a new AddressMetric which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if any parameter is {@code null}.
     */
    public static AddressMetric newAddressMetric(final ConnectionStatus status, @Nullable final String statusDetails,
            final long messageCount, @Nullable final Instant lastMessageAt) {
        return ImmutableAddressMetric.of(status, statusDetails, messageCount, lastMessageAt);
    }

    /**
     * Creates a new {@code AddressMetric} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the Connection to be created.
     * @return a new AddressMetric which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static AddressMetric addressMetricFromJson(final JsonObject jsonObject) {
        return ImmutableAddressMetric.fromJson(jsonObject);
    }

    /**
     * Returns a new {@code MappingContext}.
     *
     * @return the created MappingContext.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static MappingContext newMappingContext(final String mappingEngine, final Map<String, String> options) {
        return ImmutableMappingContext.of(mappingEngine, options);
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

    public static Source newSource(final Set<String> addresses, final int consumerCount) {
        return ImmutableSource.of(addresses, consumerCount);
    }

    public static Source newSource(final int consumerCount, final String... sources) {
        return ImmutableSource.of(consumerCount, sources);
    }

    public static Target newTarget(final String address, final Set<String> topics) {
        return ImmutableTarget.of(address, topics);
    }

    public static Target newTarget(final String address, final String requiredTopic, final String... topics) {
        return ImmutableTarget.of(address, requiredTopic, topics);
    }

}
