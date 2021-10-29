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
package org.eclipse.ditto.connectivity.model;

import java.time.Duration;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonField;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

/**
 * Factory to create new {@link Connection} instances.
 */
@Immutable
public final class ConnectivityModelFactory {

    /**
     * Template placeholder for {@link Source} address replacement.
     */
    public static final String SOURCE_ADDRESS_ENFORCEMENT = "{{ source:address }}";

    private static final HeaderMapping EMPTY_HEADER_MAPPING =
            ConnectivityModelFactory.newHeaderMapping(new HashMap<>());

    private ConnectivityModelFactory() {
        throw new AssertionError();
    }

    /**
     * Returns a new {@code ConnectionBuilder} with the required fields set.
     *
     * @param id the connection identifier.
     * @param connectionType the connection type.
     * @param connectionStatus the connection status.
     * @param uri the connection URI.
     * @return the ConnectionBuilder.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static ConnectionBuilder newConnectionBuilder(final ConnectionId id,
            final ConnectionType connectionType,
            final ConnectivityStatus connectionStatus,
            final String uri) {

        return ImmutableConnection.getBuilder(id, connectionType, connectionStatus, uri);
    }

    /**
     * Returns a mutable builder with a fluent API for an immutable {@link Connection}. The builder is initialised with
     * the values of the given Connection.
     *
     * @param connection the Connection which provides the initial values of the builder.
     * @return the new builder.
     * @throws NullPointerException if {@code connection} is {@code null}.
     */
    public static ConnectionBuilder newConnectionBuilder(final Connection connection) {
        return ImmutableConnection.getBuilder(connection);
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
     * Creates a new {@code Measurement} with the provided parameters.
     *
     * @param metricType the type of the metric.
     * @param success whether the measurement is a success or failure measurement.
     * @param values the values of the measurement.
     * @param lastMessageAt when the last message of this measurement was received.
     * @return the created {@code Measurement}
     */
    public static Measurement newMeasurement(final MetricType metricType,
            final boolean success,
            final Map<Duration, Long> values,
            @Nullable final Instant lastMessageAt) {

        return new ImmutableMeasurement(metricType, success, values, lastMessageAt);
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
     * @param inboundMetrics the overall inbound metrics.
     * @param outboundMetrics the overall outbound metrics.
     * @return a new ConnectionMetrics.
     * @throws NullPointerException if {@code connectionStatus} is {@code null}.
     */
    public static ConnectionMetrics newConnectionMetrics(final AddressMetric inboundMetrics,
            final AddressMetric outboundMetrics) {
        return ImmutableConnectionMetrics.of(inboundMetrics, outboundMetrics);
    }

    /**
     * @return a new ConnectionMetrics which is empty
     */
    public static ConnectionMetrics emptyConnectionMetrics() {
        return ImmutableConnectionMetrics.of(emptyAddressMetric(), emptyAddressMetric());
    }

    /**
     * Returns a new {@code SourceMetrics}.
     *
     * @param addressMetrics the AddressMetrics of all addresses in the source
     * @return a new SourceMetrics which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code connectionStatus} is {@code null}.
     */
    public static SourceMetrics newSourceMetrics(final Map<String, AddressMetric> addressMetrics) {
        return ImmutableSourceMetrics.of(addressMetrics);
    }

    /**
     * @return a new SourceMetrics which is empty
     */
    public static SourceMetrics emptySourceMetrics() {
        return ImmutableSourceMetrics.of(Collections.emptyMap());
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
     * @return a new SourceMetrics which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code connectionStatus} is {@code null}.
     */
    public static TargetMetrics newTargetMetrics(final Map<String, AddressMetric> addressMetrics) {
        return ImmutableTargetMetrics.of(addressMetrics);
    }

    /**
     * @return a new TargetMetrics which is empty
     */
    public static TargetMetrics emptyTargetMetrics() {
        return ImmutableTargetMetrics.of(Collections.emptyMap());
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
     * Returns a new client {@code ResourceStatus}.
     *
     * @param client a client identifier e.g. on which node this client is running
     * @param status the ConnectionStatus of the source metrics to create
     * @param statusDetails the optional details about the connection status
     * @return a new AddressMetric which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if any parameter is {@code null}.
     */
    public static ResourceStatus newClientStatus(final String client, final ConnectivityStatus status,
            @Nullable final String statusDetails) {

        return ImmutableResourceStatus.of(ResourceStatus.ResourceType.CLIENT, client, status, null, statusDetails);
    }

    /**
     * Returns a new source {@code ResourceStatus}.
     *
     * @param client a client identifier e.g. on which node this client is running
     * @param status the ConnectionStatus of the source metrics to create
     * @param statusDetails the optional details about the connection status
     * @param inStateSince the instant since the resource is in the given state
     * @return a new AddressMetric which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if any non-nullable argument is {@code null}.
     */
    public static ResourceStatus newClientStatus(final String client,
            final ConnectivityStatus status,
            @Nullable final String statusDetails,
            @Nullable final Instant inStateSince) {

        return ImmutableResourceStatus.of(ResourceStatus.ResourceType.CLIENT, client, status, null, statusDetails,
                inStateSince);
    }


    /**
     * Returns a new ssh tunnel {@code ResourceStatus}.
     *
     * @param client a client identifier e.g. on which node this client is running
     * @param status the ConnectionStatus of the source metrics to create
     * @param statusDetails the optional details about the connection status
     * @param inStateSince the instant since the resource is in the given state
     * @return a new AddressMetric which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if any non-nullable argument is {@code null}.
     * @since 2.0.0
     */
    public static ResourceStatus newSshTunnelStatus(final String client,
            final ConnectivityStatus status,
            @Nullable final String statusDetails,
            @Nullable final Instant inStateSince) {

        return ImmutableResourceStatus.of(ResourceStatus.ResourceType.SSH_TUNNEL, client, status, null, statusDetails,
                inStateSince);
    }

    /**
     * Returns a new source {@code ResourceStatus}.
     *
     * @param client a client identifier e.g. on which node this client is running
     * @param status the ConnectionStatus of the source metrics to create
     * @param address the address identifier
     * @param statusDetails the optional details about the connection status
     * @return a new AddressMetric which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if any parameter is {@code null}.
     */
    public static ResourceStatus newSourceStatus(final String client,
            final ConnectivityStatus status,
            @Nullable final String address,
            @Nullable final String statusDetails) {

        return ImmutableResourceStatus.of(ResourceStatus.ResourceType.SOURCE, client, status, address, statusDetails);
    }

    /**
     * Returns a new source {@code ResourceStatus}.
     *
     * @param client a client identifier e.g. on which node this client is running
     * @param status the ConnectionStatus of the source metrics to create
     * @param address the address identifier
     * @param statusDetails the optional details about the connection status
     * @param inStatusSince the instant since the target is in the given state
     * @return a new AddressMetric which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if any non-nullable argument is {@code null}.
     * @since 2.1.0
     */
    public static ResourceStatus newSourceStatus(final String client,
            final ConnectivityStatus status,
            @Nullable final String address,
            @Nullable final String statusDetails,
            @Nullable final Instant inStatusSince) {

        return ImmutableResourceStatus.of(ResourceStatus.ResourceType.SOURCE, client, status, address, statusDetails,
                inStatusSince);
    }

    /**
     * Returns a new target {@code ResourceStatus}.
     *
     * @param client a client identifier e.g. on which node this client is running
     * @param status the ConnectionStatus of the source metrics to create
     * @param address the address identifier
     * @param statusDetails the optional details about the connection status
     * @return a new AddressMetric which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if any parameter is {@code null}.
     */
    public static ResourceStatus newTargetStatus(final String client,
            final ConnectivityStatus status,
            @Nullable final String address,
            @Nullable final String statusDetails) {

        return ImmutableResourceStatus.of(ResourceStatus.ResourceType.TARGET, client, status, address,
                statusDetails);
    }

    /**
     * Returns a new target {@code ResourceStatus}.
     *
     * @param client a client identifier e.g. on which node this client is running
     * @param status the ConnectionStatus of the source metrics to create
     * @param address the address identifier
     * @param statusDetails the optional details about the connection status
     * @param inStatusSince the instant since the target is in the given state
     * @return a new AddressMetric which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if any non-nullable argument is {@code null}.
     * @since 2.1.0
     */
    public static ResourceStatus newTargetStatus(final String client,
            final ConnectivityStatus status,
            @Nullable final String address,
            @Nullable final String statusDetails,
            @Nullable final Instant inStatusSince) {

        return ImmutableResourceStatus.of(ResourceStatus.ResourceType.TARGET, client, status, address,
                statusDetails, inStatusSince);
    }

    /**
     * Returns a new generic {@code ResourceStatus} update.
     *
     * @param client a client identifier e.g. on which node this client is running
     * @param status the ConnectionStatus of the source metrics to create
     * @param address the address identifier
     * @param statusDetails the optional details about the connection status
     * @return a new AddressMetric which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if any parameter is {@code null}.
     */
    public static ResourceStatus newStatusUpdate(final String client,
            final ConnectivityStatus status,
            @Nullable final String address,
            @Nullable final String statusDetails) {

        return ImmutableResourceStatus.of(ResourceStatus.ResourceType.UNKNOWN, client, status, address, statusDetails);
    }

    /**
     * Returns a new target {@code ResourceStatus}.
     *
     * @param client a client identifier e.g. on which node this client is running
     * @param status the ConnectionStatus of the source metrics to create
     * @param address the address identifier
     * @param statusDetails the optional details about the connection status
     * @param inStatusSince the instant since the resource is in the described status
     * @return a new AddressMetric which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if any non-nullable argument is {@code null}.
     */
    public static ResourceStatus newStatusUpdate(final String client,
            final ConnectivityStatus status,
            @Nullable final String address,
            @Nullable final String statusDetails,
            @Nullable final Instant inStatusSince) {

        return ImmutableResourceStatus.of(ResourceStatus.ResourceType.UNKNOWN, client, status, address,
                statusDetails, inStatusSince);
    }

    /**
     * Returns a new target {@code ResourceStatus}.
     *
     * @param resourceType the resource type, e.g. {@code client}
     * @param client a client identifier e.g. on which node this client is running
     * @param status the ConnectionStatus of the source metrics to create
     * @param address the optional address identifier
     * @param statusDetails the optional details about the connection status
     * @param inStatusSince the optional instant since the resource is in the described status
     * @return a new AddressMetric which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if any parameter is {@code null}.
     */
    public static ResourceStatus newStatusUpdate(final ResourceStatus.ResourceType resourceType,
            final String client,
            final ConnectivityStatus status,
            @Nullable final String address,
            @Nullable final String statusDetails,
            @Nullable final Instant inStatusSince) {

        return ImmutableResourceStatus.of(resourceType, client, status, address,
                statusDetails, inStatusSince);
    }

    /**
     * Creates a new {@code ResourceStatus} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the ResourceStatus to be created.
     * @return a new ResourceStatus which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static ResourceStatus resourceStatusFromJson(final JsonObject jsonObject) {
        return ImmutableResourceStatus.fromJson(jsonObject);
    }

    /**
     * Returns a new {@code AddressMetric}.
     *
     * @param measurements set of measurements for this address
     * @return a new AddressMetric which is initialised with the given measurements.
     * @throws NullPointerException if any parameter is {@code null}.
     */
    public static AddressMetric newAddressMetric(final Set<Measurement> measurements) {
        return ImmutableAddressMetric.of(measurements);
    }

    /**
     * Merges an existing {@code AddressMetric} with additional measurements to a new {@code AddressMetric}.
     *
     * @param addressMetric the existing address metric
     * @param additionalMeasurements the additional measurements
     * @return a new AddressMetric with the existing and additional measurements merged.
     * @throws NullPointerException if any parameter is {@code null}.
     */
    public static AddressMetric newAddressMetric(final AddressMetric addressMetric,
            final Collection<Measurement> additionalMeasurements) {

        final Set<Measurement> set = new LinkedHashSet<>(addressMetric.getMeasurements());
        set.addAll(additionalMeasurements);
        return ImmutableAddressMetric.of(set);
    }

    /**
     * Returns a new empty {@code AddressMetric}.
     *
     * @return a new AddressMetric which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if any parameter is {@code null}.
     */
    public static AddressMetric emptyAddressMetric() {
        return ImmutableAddressMetric.of(Collections.emptySet());
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
     * @param mappingEngine fully qualified classname of a mapping engine.
     * @param options the mapping options required to instantiate a mapper.
     * @return the created MappingContext.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 1.3.0
     */
    public static MappingContextBuilder newMappingContextBuilder(final String mappingEngine,
            final JsonObject options) {
        return new ImmutableMappingContext.Builder(mappingEngine, options);
    }

    /**
     * Returns a new {@code MappingContext}.
     *
     * @param mappingEngine fully qualified classname of a mapping engine.
     * @param options the mapping options required to instantiate a mapper.
     * @return the created MappingContext.
     * @throws NullPointerException if any argument is {@code null}.
     */
    public static MappingContext newMappingContext(final String mappingEngine, final Map<String, String> options) {
        return newMappingContextBuilder(mappingEngine, options.entrySet()
                .stream()
                .map(entry -> JsonField.newInstance(entry.getKey(), JsonValue.of(entry.getValue())))
                .collect(JsonCollectors.fieldsToObject())).build();
    }

    /**
     * Returns a new {@code MappingContext}.
     *
     * @param mappingEngine fully qualified classname of a mapping engine.
     * @param options the mapping options required to instantiate a mapper.
     * @param incomingConditions the conditions to be checked before mapping incoming messages.
     * @param outgoingConditions the conditions to be checked before mapping outgoing messages.
     * @return the created MappingContext.
     * @throws NullPointerException if any argument is {@code null}.
     * @since 1.3.0
     */
    public static MappingContext newMappingContext(final String mappingEngine, final JsonObject options,
            final Map<String, String> incomingConditions, final Map<String, String> outgoingConditions) {
        return newMappingContextBuilder(mappingEngine, options).incomingConditions(incomingConditions)
                .outgoingConditions(outgoingConditions)
                .build();
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
     * Creates a new {@code Map<String, MappingContext>} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the MappingContext to be created.
     * @return a new map which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    static Map<String, MappingContext> mappingsFromJson(final JsonObject jsonObject) {
        return jsonObject.stream()
                .filter(f -> f.getValue().isObject())
                .map(field -> {
                    final String id = field.getKeyName();
                    final MappingContext context = ImmutableMappingContext.fromJson(field.getValue().asObject());
                    return new AbstractMap.SimpleImmutableEntry<>(id, context);
                }).collect(fromEntries());
    }

    private static <K, V> Collector<Map.Entry<K, V>, ?, Map<K, V>> fromEntries() {
        return Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    /**
     * @return new empty {@link PayloadMappingDefinition}
     */
    public static PayloadMappingDefinition emptyPayloadMappingDefinition() {
        return ImmutablePayloadMappingDefinition.empty();
    }

    /**
     * @param definitions the existing definitions
     * @return new instance of {@link PayloadMappingDefinition} initialized with the given definitions
     */
    public static PayloadMappingDefinition newPayloadMappingDefinition(final Map<String, MappingContext> definitions) {
        return ImmutablePayloadMappingDefinition.from(definitions);
    }

    /**
     * @param id ID of the mapping
     * @param mappingContext config of the mapping
     * @return new instance of {@link PayloadMappingDefinition} initialized with the given definition
     */
    public static PayloadMappingDefinition newPayloadMappingDefinition(final String id,
            final MappingContext mappingContext) {
        final Map<String, MappingContext> definitions = new HashMap<>();
        definitions.put(id, mappingContext);
        return ImmutablePayloadMappingDefinition.from(definitions);
    }

    /**
     * @return new instance of empty {@link PayloadMapping}
     */
    public static PayloadMapping emptyPayloadMapping() {
        return ImmutablePayloadMapping.empty();
    }

    /**
     * @return new instance of {@link PayloadMapping} initialized with the given mappings
     */
    public static PayloadMapping newPayloadMapping(final List<String> mappings) {
        return ImmutablePayloadMapping.from(mappings);
    }

    /**
     * @return new instance of {@link PayloadMapping} initialized with the given mappings
     */
    public static PayloadMapping newPayloadMapping(@Nullable final String... mappings) {
        if (mappings == null || mappings.length == 0) {
            return emptyPayloadMapping();
        } else {
            return ImmutablePayloadMapping.from(Arrays.asList(mappings));
        }
    }

    /**
     * Creates a new {@code PayloadMapping} object from the specified JSON object.
     *
     * @param jsonArray a JSON array which provides the data for the PayloadMapping to be created.
     * @return a new PayloadMapping which is initialised with the extracted data from {@code jsonArray}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static PayloadMapping newPayloadMapping(final JsonArray jsonArray) {
        return ImmutablePayloadMapping.fromJson(jsonArray);
    }

    /**
     * Creates a new {@link SourceBuilder} for building {@link Source}s.
     *
     * @return new {@link Source} builder
     */
    @SuppressWarnings({"rawtypes", "java:S3740"})
    public static SourceBuilder newSourceBuilder() {
        return new ImmutableSource.Builder();
    }

    /**
     * Creates a new {@link SourceBuilder} for building {@link Source}s.
     *
     * @return new {@link Source} builder
     */
    @SuppressWarnings({"rawtypes", "java:S3740"})
    public static SourceBuilder newSourceBuilder(final Source source) {
        return new ImmutableSource.Builder(source);
    }

    /**
     * Creates a new {@link Source}.
     *
     * @param authorizationContext the authorization context
     * @param address the source address where messages are consumed from
     * @return the created {@link Source}
     */
    public static Source newSource(final AuthorizationContext authorizationContext, final String address) {
        return newSourceBuilder().address(address).authorizationContext(authorizationContext).build();
    }

    /**
     * Creates a new {@link Source}.
     *
     * @param authorizationContext the authorization context
     * @param address the source address where messages are consumed from
     * @param index the index inside the connection
     * @return the created {@link Source}
     */
    public static Source newSource(final AuthorizationContext authorizationContext, final String address,
            final int index) {

        return newSourceBuilder().address(address).authorizationContext(authorizationContext).index(index).build();
    }

    /**
     * Creates a new {@link TargetBuilder} for building {@link Target}s.
     *
     * @return new {@link Target} builder
     */
    public static TargetBuilder newTargetBuilder() {
        return new ImmutableTarget.Builder();
    }

    /**
     * Creates a new {@link TargetBuilder} for building {@link Target}s.
     *
     * @return new {@link Target} builder
     */
    public static TargetBuilder newTargetBuilder(final Target target) {
        return new ImmutableTarget.Builder(target);
    }

    /**
     * Creates a new {@link Target} from existing target but different address.
     *
     * @param target the target
     * @param address the address where the signals will be published
     * @param qos the qos of the new Target (e.g. for MQTT targets)
     * @return the created {@link Target}
     */
    public static Target newTarget(final Target target, final String address, @Nullable final Integer qos) {
        return newTargetBuilder()
                .address(address)
                .originalAddress(target.getOriginalAddress())
                .authorizationContext(target.getAuthorizationContext())
                .headerMapping(target.getHeaderMapping())
                .qos(qos)
                .topics(target.getTopics())
                .build();
    }

    /**
     * Creates a new {@link Target} from existing target but different address and acknowledgement.
     *
     * @param target the target
     * @param address the address where the signals will be published
     * @param qos the qos of the new Target (e.g. for MQTT targets)
     * @param label the {@link AcknowledgementLabel} of the new Target
     * @return the created {@link Target}
     * @since 1.2.0
     */
    public static Target newTarget(final Target target, final String address, @Nullable final Integer qos, final
    AcknowledgementLabel label) {
        return newTargetBuilder()
                .address(address)
                .originalAddress(target.getOriginalAddress())
                .authorizationContext(target.getAuthorizationContext())
                .headerMapping(target.getHeaderMapping())
                .qos(qos)
                .issuedAcknowledgementLabel(label)
                .topics(target.getTopics())
                .build();
    }

    /**
     * Creates a new {@link SshTunnelBuilder} for building {@link SshTunnel}s.
     *
     * @param enabled sets if the ssh tunnel is active
     * @param credentials the credentials of the ssh tunnel
     * @param uri the uri of the ssh tunnel
     * @return new {@link SshTunnelBuilder} builder
     * @since 2.0.0
     */
    public static SshTunnelBuilder newSshTunnelBuilder(final boolean enabled, final Credentials credentials,
            final String uri) {
        return new ImmutableSshTunnel.Builder(enabled, credentials, uri);
    }

    /**
     * Creates a new {@link SshTunnelBuilder} for building {@link SshTunnel}s.
     *
     * @return new {@link SshTunnelBuilder} builder
     * @since 2.0.0
     */
    public static SshTunnelBuilder newSshTunnelBuilder(final SshTunnel sshTunnel) {
        return new ImmutableSshTunnel.Builder(sshTunnel);
    }

    /**
     * Creates a new {@code SshTunnel} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the SshTunnel to be created.
     * @return a new SshTunnel which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     * @since 2.0.0
     */
    public static SshTunnel sshTunnelFromJson(final JsonObject jsonObject) {
        return ImmutableSshTunnel.fromJson(jsonObject);
    }

    /**
     * Creates a new {@code SshTunnel}.
     *
     * @param enabled {@code true} if the ssh tunnel is enabled
     * @param credentials the credentials of the ssh tunnel
     * @param validateHost {@code true} if host validation is enabled
     * @param knownHosts the known hosts of the ssh tunnel
     * @param uri the uri of the ssh tunnel
     * @return the created {@link SshTunnel}
     * @since 2.0.0
     */
    public static SshTunnel newSshTunnel(final boolean enabled, final Credentials credentials,
            final boolean validateHost, final List<String> knownHosts, final String uri) {
        return new ImmutableSshTunnel.Builder(enabled, credentials, validateHost, knownHosts, uri).build();
    }

    /**
     * Creates a new {@code SshTunnel} without knownHosts.
     *
     * @param enabled {@code true} if the ssh tunnel is enabled
     * @param credentials the credentials of the ssh tunnel
     * @param uri the uri of the ssh tunnel
     * @return the created {@link SshTunnel}
     * @since 2.0.0
     */
    public static SshTunnel newSshTunnel(final boolean enabled, final Credentials credentials, final String uri) {
        return new ImmutableSshTunnel.Builder(enabled, credentials, uri).build();
    }

    /**
     * Creates a new {@code Source} object from the specified JSON object. Decides which specific {@link Source}
     * implementation to choose depending on the given {@link ConnectionType}.
     *
     * @param jsonObject a JSON object which provides the data for the Source to be created.
     * @param index the index to distinguish between sources that would otherwise be different
     * @return a new Source which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static Source sourceFromJson(final JsonObject jsonObject, final int index) {
        return ImmutableSource.fromJson(jsonObject, index);
    }

    /**
     * Creates a new {@code Target} object from the specified JSON object. Decides which specific {@link Target}
     * implementation to choose depending on the given {@link ConnectionType}.
     *
     * @param jsonObject a JSON object which provides the data for the Target to be created.
     * @return a new Target is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static Target targetFromJson(final JsonObject jsonObject) {
        return ImmutableTarget.fromJson(jsonObject);
    }

    /**
     * Creates a new {@code FilteredTopic} from the passed {@code topicString} which consists of a {@code Topic} and an
     * optional filter string supplied with {@code ?filter=...}.
     *
     * @param topicString the {@code FilteredTopic} String representation
     * @return the created FilteredTopic
     */
    public static FilteredTopic newFilteredTopic(final String topicString) {
        return ImmutableFilteredTopic.fromString(topicString);
    }

    /**
     * Returns a builder for a {@link FilteredTopic}.
     *
     * @param topic the topic of the FilteredTopic to be built.
     * @return the builder.
     * @throws NullPointerException if {@code topic} is {@code null}.
     */
    public static FilteredTopicBuilder newFilteredTopicBuilder(final Topic topic) {
        return ImmutableFilteredTopic.getBuilder(topic);
    }

    /**
     * New instance of {@link Enforcement} options.
     *
     * @param input the input that is compared against the filters.
     * @param filters additional filters.
     * @return the enforcement instance.
     */
    public static Enforcement newEnforcement(final String input, final Set<String> filters) {
        return ImmutableEnforcement.of(input, filters);
    }

    /**
     * New instance of {@link Enforcement} options.
     *
     * @param input the input that is compared with the filters.
     * @param requiredFilter the required filter.
     * @param additionalFilters additional filters.
     * @return the enforcement instance.
     */
    public static Enforcement newEnforcement(final String input, final String requiredFilter,
            final String... additionalFilters) {

        final Set<String> filters = new LinkedHashSet<>(1 + additionalFilters.length);
        filters.add(requiredFilter);
        Collections.addAll(filters, additionalFilters);

        return newEnforcement(input, filters);
    }

    /**
     * New instance of {@link Enforcement} options to be used with connections supporting filtering on their
     * {@link Source} {@code address}.
     *
     * @param filters the filters.
     * @return the enforcement instance.
     */
    public static Enforcement newSourceAddressEnforcement(final Set<String> filters) {
        return newEnforcement(SOURCE_ADDRESS_ENFORCEMENT, filters);
    }

    /**
     * New instance of {@link Enforcement} options to be used with connections supporting filtering on their
     * {@link Source} {@code address}.
     *
     * @param requiredFilter the required filter.
     * @param additionalFilters additional filters.
     * @return the enforcement instance.
     */
    public static Enforcement newSourceAddressEnforcement(final String requiredFilter,
            final String... additionalFilters) {

        return newEnforcement(SOURCE_ADDRESS_ENFORCEMENT, requiredFilter, additionalFilters);
    }

    /**
     * Create a copy of this object with error message set.
     *
     * @param enforcement the enforcement options.
     * @return a copy of this object.
     */
    public static Enforcement newEnforcement(final Enforcement enforcement) {
        return ImmutableEnforcement.of(enforcement.getInput(), enforcement.getFilters());
    }

    /**
     * Returns singleton of a header mapping without any mappings defined
     *
     * @return the empty header mapping
     */
    public static HeaderMapping emptyHeaderMapping() {
        return EMPTY_HEADER_MAPPING;
    }

    /**
     * Creates a new instance of a {@link HeaderMapping}.
     *
     * @param mapping the mapping definition.
     * @return the new instance of {@link HeaderMapping}.
     */
    public static HeaderMapping newHeaderMapping(final Map<String, String> mapping) {
        return new ImmutableHeaderMapping(mapping);
    }

    /**
     * Creates a new {@code HeaderMapping} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the HeaderMapping to be created.
     * @return a new HeaderMapping which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static HeaderMapping newHeaderMapping(final JsonObject jsonObject) {
        return ImmutableHeaderMapping.fromJson(jsonObject);
    }

    /**
     * Creates a new {@link LogEntry} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the LogEntry to be created.
     * @return a new LogEntry which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static LogEntry logEntryFromJson(final JsonObject jsonObject) {
        return ImmutableLogEntry.fromJson(jsonObject);
    }

    /**
     * Creates a new {@link LogEntryBuilder} with the given parameters.
     *
     * @param correlationId the correlation ID.
     * @param timestamp the timestamp of the log entry.
     * @param logCategory the category.
     * @param logType the type.
     * @param logLevel the level.
     * @param message the message.
     * @return a new builder.
     */
    public static LogEntryBuilder newLogEntryBuilder(final String correlationId,
            final Instant timestamp,
            final LogCategory logCategory,
            final LogType logType,
            final LogLevel logLevel,
            final String message) {

        return ImmutableLogEntry.getBuilder(correlationId, timestamp, logCategory, logType, logLevel, message);
    }

}
