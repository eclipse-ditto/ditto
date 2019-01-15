/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.connectivity;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.auth.AuthorizationContext;

/**
 * Factory to create new {@link Connection} instances.
 */
@Immutable
public final class ConnectivityModelFactory {

    /**
     * Template placeholder for {@link Source} address replacement.
     */
    public static final String SOURCE_ADDRESS_ENFORCEMENT = "{{ source:address }}";

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
    public static ConnectionBuilder newConnectionBuilder(final String id,
            final ConnectionType connectionType,
            final ConnectionStatus connectionStatus,
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
     * @param counterType the type of the counter (e.g. a metric label).
     * @param success whether the measurement is a success or failure measurement.
     * @param values the values of the measurement.
     * @param lastMessageAt when the last message of this measurement was received.
     * @return the created {@code Measurement}
     */
    public static Measurement newMeasurement(final String counterType, final boolean success,
            final Map<Duration, Long> values, @Nullable final Instant lastMessageAt) {
        return new ImmutableMeasurement(counterType, success, values, lastMessageAt);
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
     * @param overallMetrics the aggregated overall metrics
     * @return a new ConnectionMetrics which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code connectionStatus} is {@code null}.
     */
    public static ConnectionMetrics newConnectionMetrics(final AddressMetric overallMetrics) {
        return ImmutableConnectionMetrics.of(overallMetrics);
    }

    /**
     * @return a new ConnectionMetrics which is empty
     */
    public static ConnectionMetrics emptyConnectionMetrics() {
        return ImmutableConnectionMetrics.of(emptyAddressMetric());
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
     * @param address a client identifier e.g. on which node this client is running
     * @param status the ConnectionStatus of the source metrics to create
     * @param statusDetails the optional details about the connection status
     * @return a new AddressMetric which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if any parameter is {@code null}.
     */
    public static ResourceStatus newClientStatus(
            final String address, final ConnectionStatus status,
            @Nullable final String statusDetails) {
        return ImmutableResourceStatus.of(ResourceStatus.ResourceType.CLIENT, address, status.getName(),
                statusDetails);
    }

    /**
     * Returns a new source {@code ResourceStatus}.
     *
     * @param address a client identifier e.g. on which node this client is running
     * @param status the ConnectionStatus of the source metrics to create
     * @param statusDetails the optional details about the connection status
     * @param inStateSince the instant since the resource is in the given state
     * @return a new AddressMetric which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if any parameter is {@code null}.
     */
    public static ResourceStatus newClientStatus(
            final String address, final ConnectionStatus status,
            @Nullable final String statusDetails, final Instant inStateSince) {
        return ImmutableResourceStatus.of(ResourceStatus.ResourceType.CLIENT, address, status.getName(),
                statusDetails, inStateSince);
    }

    /**
     * Returns a new source {@code ResourceStatus}.
     *
     * @param address the address identifier
     * @param status the ConnectionStatus of the source metrics to create
     * @param statusDetails the optional details about the connection status
     * @return a new AddressMetric which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if any parameter is {@code null}.
     */
    public static ResourceStatus newSourceStatus(final String address, final ConnectionStatus status,
            @Nullable final String statusDetails) {
        return ImmutableResourceStatus.of(ResourceStatus.ResourceType.SOURCE, address, status.getName(),
                statusDetails);
    }

    /**
     * Returns a new source {@code ResourceStatus}.
     *
     * @param address the address identifier
     * @param status the ConnectionStatus of the source metrics to create
     * @param statusDetails the optional details about the connection status
     * @return a new AddressMetric which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if any parameter is {@code null}.
     */
    public static ResourceStatus newSourceStatus(final String address, final String status,
            @Nullable final String statusDetails) {
        return ImmutableResourceStatus.of(ResourceStatus.ResourceType.SOURCE, address, status,
                statusDetails);
    }

    /**
     * Returns a new target {@code ResourceStatus}.
     *
     * @param address the address identifier
     * @param status the ConnectionStatus of the source metrics to create
     * @param statusDetails the optional details about the connection status
     * @return a new AddressMetric which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if any parameter is {@code null}.
     */
    public static ResourceStatus newTargetStatus(final String address, final ConnectionStatus status,
            @Nullable final String statusDetails) {
        return ImmutableResourceStatus.of(ResourceStatus.ResourceType.TARGET, address, status.getName(),
                statusDetails);
    }

    /**
     * Returns a new generic {@code ResourceStatus} update.
     *
     * @param address the address identifier
     * @param status the ConnectionStatus of the source metrics to create
     * @param statusDetails the optional details about the connection status
     * @return a new AddressMetric which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if any parameter is {@code null}.
     */
    public static ResourceStatus newStatusUpdate(final String address, final ConnectionStatus status,
            @Nullable final String statusDetails) {
        return ImmutableResourceStatus.of(ResourceStatus.ResourceType.UNKNOWN, address, status.getName(),
                statusDetails);
    }

    /**
     * Returns a new target {@code ResourceStatus}.
     *
     * @param address the address identifier
     * @param status the ConnectionStatus of the source metrics to create
     * @param statusDetails the optional details about the connection status
     * @param inStatusSince the instant since the resource is in the described status
     * @return a new AddressMetric which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if any parameter is {@code null}.
     */
    public static ResourceStatus newStatusUpdate(final String address, final ConnectionStatus status,
            @Nullable final String statusDetails,
            final Instant inStatusSince) {
        return ImmutableResourceStatus.of(ResourceStatus.ResourceType.UNKNOWN, address, status.getName(),
                statusDetails, inStatusSince);
    }

    /**
     * Creates a new {@code ResourceStatus} object from the specified JSON object.
     *
     * @param jsonObject a JSON object which provides the data for the ResourceStatus to be created.
     * @param type a resource type
     * @return a new ResourceStatus which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static ResourceStatus resourceStatusFromJson(final JsonObject jsonObject,
            final ResourceStatus.ResourceType type) {
        return ImmutableResourceStatus.fromJson(jsonObject, type);
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
            final Set<Measurement> additionalMeasurements) {
        final Set<Measurement> set = new HashSet<>(addressMetric.getMeasurements());
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
     * @param mappingEngine fully qualified classname of a mapping engine
     * @param options the mapping options required to instantiate a mapper
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
     * Creates a new {@link SourceBuilder} for building {@link Source}s.
     *
     * @return new {@link Source} builder
     */
    public static SourceBuilder newSourceBuilder() {
        return new ImmutableSource.Builder();
    }

    /**
     * Creates a new {@link MqttSourceBuilder} for building {@link MqttSource}s.
     *
     * @return new {@link MqttSource} builder
     */
    public static MqttSourceBuilder newMqttSourceBuilder() {
        return new ImmutableMqttSource.Builder();
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
     * Creates a new {@link Target} from existing target but different address.
     *
     * @param target the target
     * @param address the address where the signals will be published
     * @return the created {@link Target}
     */
    public static Target newTarget(final Target target, final String address) {
        return newTarget(address, target.getOriginalAddress(),
                target.getAuthorizationContext(),
                target.getHeaderMapping().orElse(null),
                target.getTopics());
    }

    /**
     * Creates a new {@link Target}.
     *
     * @param address the address where the signals will be published
     * @param authorizationContext the authorization context of the new {@link Target}
     * @param headerMapping the {@link HeaderMapping} of the new Target
     * @param topics the FilteredTopics for which this target will receive signals
     * @return the created {@link Target}
     */
    public static Target newTarget(final String address, final AuthorizationContext authorizationContext,
            @Nullable final HeaderMapping headerMapping, final Set<FilteredTopic> topics) {
        return new ImmutableTarget.Builder()
                .address(address)
                .originalAddress(address) // addresses are the same before placeholders are resolved
                .authorizationContext(authorizationContext)
                .topics(topics)
                .headerMapping(headerMapping)
                .build();
    }

    /**
     * Creates a new {@link Target}.
     *
     * @param address the address where the signals will be published
     * @param originalAddress address the address before placeholders were resolved
     * @param authorizationContext the authorization context of the new {@link Target}
     * @param headerMapping the {@link HeaderMapping} of the new Target
     * @param topics the FilteredTopics for which this target will receive signals
     * @return the created {@link Target}
     */
    public static Target newTarget(final String address, final String originalAddress,
            final AuthorizationContext authorizationContext,
            @Nullable final HeaderMapping headerMapping, final Set<FilteredTopic> topics) {
        return new ImmutableTarget.Builder()
                .address(address)
                .originalAddress(originalAddress) // addresses are the same before placeholders are resolved
                .authorizationContext(authorizationContext)
                .topics(topics)
                .headerMapping(headerMapping)
                .build();
    }

    /**
     * Creates a new {@link Target}.
     *
     * @param address the address where the signals will be published
     * @param authorizationContext the authorization context of the new {@link Target}
     * @param headerMapping the {@link HeaderMapping} of the new Target
     * @param requiredTopic the required FilteredTopic that should be published via this target
     * @param additionalTopics additional set of FilteredTopics that should be published via this target
     * @return the created {@link Target}
     */
    public static Target newTarget(final String address, final AuthorizationContext authorizationContext,
            @Nullable final HeaderMapping headerMapping, final FilteredTopic requiredTopic,
            final FilteredTopic... additionalTopics) {
        final HashSet<FilteredTopic> topics = new HashSet<>(Collections.singletonList(requiredTopic));
        topics.addAll(Arrays.asList(additionalTopics));
        return newTarget(address, authorizationContext, headerMapping, topics);
    }

    /**
     * Creates a new {@link Target}.
     *
     * @param address the address where the signals will be published
     * @param authorizationContext the authorization context of the new {@link Target}
     * @param headerMapping the {@link HeaderMapping} of the new Target
     * @param requiredTopic the required topic that should be published via this target
     * @param additionalTopics additional set of topics that should be published via this target
     * @return the created {@link Target}
     */
    public static Target newTarget(final String address, final AuthorizationContext authorizationContext,
            @Nullable final HeaderMapping headerMapping, final Topic requiredTopic, final Topic... additionalTopics) {
        final HashSet<Topic> topics = new HashSet<>(Collections.singletonList(requiredTopic));
        topics.addAll(Arrays.asList(additionalTopics));
        return newTarget(address, authorizationContext, headerMapping, topics.stream()
                .map(ConnectivityModelFactory::newFilteredTopic)
                .collect(Collectors.toSet())
        );
    }

    /**
     * Creates a new {@link MqttTarget} with MQTT specific configuration.
     *
     * @param address the address where the signals will be published
     * @param authorizationContext the authorization context of the new {@link Target}
     * @param qos the target qos value
     * @param requiredTopic the required topic that should be published via this target
     * @param additionalTopics additional set of topics that should be published via this target
     * @return the created {@link Target}
     */
    public static MqttTarget newMqttTarget(final String address,
            final AuthorizationContext authorizationContext,
            final int qos,
            final Topic requiredTopic,
            final Topic... additionalTopics) {
        final HashSet<Topic> topics = new HashSet<>(Collections.singletonList(requiredTopic));
        topics.addAll(Arrays.asList(additionalTopics));
        final Target target = newTarget(address, authorizationContext, null, topics.stream()
                .map(ConnectivityModelFactory::newFilteredTopic)
                .collect(Collectors.toSet()));
        return new ImmutableMqttTarget(target, qos);
    }

    /**
     * Creates a new {@link MqttTarget} with MQTT specific configuration.
     *
     * @param target the delegate target
     * @param qos the target qos value
     * @return the created {@link Target}
     */
    public static MqttTarget newMqttTarget(final Target target, final int qos) {
        return new ImmutableMqttTarget(target, qos);
    }

    /**
     * Creates a new {@code Source} object from the specified JSON object. Decides which specific {@link Source}
     * implementation to choose depending on the given {@link ConnectionType}.
     *
     * @param jsonObject a JSON object which provides the data for the Source to be created.
     * @param index the index to distinguish between sources that would otherwise be different
     * @param type the connection type required to decide which iplementation of {@link Source} to choose
     * @return a new Source which is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static Source sourceFromJson(final JsonObject jsonObject, final int index, final ConnectionType type) {
        switch (type) {
            case AMQP_091:
            case AMQP_10:
                return ImmutableSource.fromJson(jsonObject, index);
            case MQTT:
                return ImmutableMqttSource.fromJson(jsonObject, index);
            default:
                throw ConnectionConfigurationInvalidException
                        .newBuilder("Unexpected connection type <" + type + ">")
                        .build();
        }
    }

    /**
     * Creates a new {@code Target} object from the specified JSON object. Decides which specific {@link Target}
     * implementation to choose depending on the given {@link ConnectionType}.
     *
     * @param jsonObject a JSON object which provides the data for the Target to be created.
     * @param type the connection type required to decide which iplementation of {@link Source} to choose
     * @return a new Source Target is initialised with the extracted data from {@code jsonObject}.
     * @throws NullPointerException if {@code jsonObject} is {@code null}.
     * @throws org.eclipse.ditto.json.JsonParseException if {@code jsonObject} is not an appropriate JSON object.
     */
    public static Target targetFromJson(final JsonObject jsonObject, final ConnectionType type) {
        switch (type) {
            case AMQP_091:
            case AMQP_10:
                return ImmutableTarget.fromJson(jsonObject);
            case MQTT:
                return ImmutableMqttTarget.fromJson(jsonObject);
            default:
                throw ConnectionConfigurationInvalidException
                        .newBuilder("Unexpected connection type <" + type + ">")
                        .build();
        }
    }

    /**
     * Creates a new {@code FilteredTopic} without the optional {@code filter} String.
     *
     * @param topic the {@code Topic} of the FilteredTopic
     * @return the created FilteredTopic
     */
    public static FilteredTopic newFilteredTopic(final Topic topic) {
        return ImmutableFilteredTopic.of(topic, Collections.emptyList(), null);
    }

    /**
     * Creates a new {@code FilteredTopic} with the optional {@code filter} String.
     *
     * @param topic the {@code Topic} of the FilteredTopic
     * @param filter the filter String to apply for the FilteredTopic
     * @return the created FilteredTopic
     */
    public static FilteredTopic newFilteredTopic(final Topic topic, @Nullable final String filter) {
        return ImmutableFilteredTopic.of(topic, Collections.emptyList(), filter);
    }

    /**
     * Creates a new {@code FilteredTopic} with the passed {@code namespaces}.
     *
     * @param topic the {@code Topic} of the FilteredTopic
     * @param namespaces the namespaces for which the filter should be applied - if empty, all namespaces are
     * considered
     * @return the created FilteredTopic
     */
    public static FilteredTopic newFilteredTopic(final Topic topic, final List<String> namespaces) {
        return ImmutableFilteredTopic.of(topic, namespaces, null);
    }

    /**
     * Creates a new {@code FilteredTopic} with the passed {@code namespaces} and the optional {@code filter} String.
     *
     * @param topic the {@code Topic} of the FilteredTopic
     * @param namespaces the namespaces for which the filter should be applied - if empty, all namespaces are
     * considered
     * @param filter the filter String to apply for the FilteredTopic
     * @return the created FilteredTopic
     */
    public static FilteredTopic newFilteredTopic(final Topic topic, final List<String> namespaces,
            @Nullable final String filter) {
        return ImmutableFilteredTopic.of(topic, namespaces, filter);
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
     * New instance of {@link Enforcement} options.
     *
     * @param input the input that is compared against the filters
     * @param filters additional filters
     * @return the enforcement instance
     */
    public static Enforcement newEnforcement(final String input, final Set<String> filters) {
        return ImmutableEnforcement.of(input, filters);
    }

    /**
     * New instance of {@link Enforcement} options.
     *
     * @param input the input that is compared with the filters
     * @param requiredFilter the required filter
     * @param additionalFilters additional filters
     * @return the enforcement instance
     */
    public static Enforcement newEnforcement(final String input, final String requiredFilter,
            final String... additionalFilters) {
        final HashSet<String> filters = new HashSet<>(Collections.singletonList(requiredFilter));
        filters.addAll(Arrays.asList(additionalFilters));
        return newEnforcement(input, filters);
    }

    /**
     * New instance of {@link Enforcement} options to be used with connections supporting filtering on their {@link
     * Source} {@code address}.
     *
     * @param filters the filters
     * @return the enforcement instance
     */
    public static Enforcement newSourceAddressEnforcement(final Set<String> filters) {
        return newEnforcement(SOURCE_ADDRESS_ENFORCEMENT, filters);
    }

    /**
     * New instance of {@link Enforcement} options to be used with connections supporting filtering on their {@link
     * Source} {@code address}.
     *
     * @param requiredFilter the required filter
     * @param additionalFilters additional filters
     * @return the enforcement instance
     */
    public static Enforcement newSourceAddressEnforcement(final String requiredFilter,
            final String... additionalFilters) {
        return newEnforcement(SOURCE_ADDRESS_ENFORCEMENT, requiredFilter, additionalFilters);
    }

    /**
     * Create a copy of this object with error message set.
     *
     * @param enforcement the enforcement options
     * @return a copy of this object.
     */
    public static Enforcement newEnforcement(final Enforcement enforcement) {
        return ImmutableEnforcement.of(enforcement.getInput(), enforcement.getFilters());
    }

    /**
     * Creates a new instance of a {@link HeaderMapping}.
     *
     * @param mapping the mapping definition
     * @return the new instance of {@link HeaderMapping}
     */
    public static HeaderMapping newHeaderMapping(Map<String, String> mapping) {
        return new ImmutableHeaderMapping(mapping);
    }

}
