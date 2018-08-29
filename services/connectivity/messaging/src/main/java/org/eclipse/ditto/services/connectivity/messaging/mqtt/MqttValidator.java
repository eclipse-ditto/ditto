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
package org.eclipse.ditto.services.connectivity.messaging.mqtt;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.MqttSource;
import org.eclipse.ditto.model.connectivity.MqttTarget;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.connectivity.messaging.PlaceholderFilter;
import org.eclipse.ditto.services.connectivity.messaging.validation.AbstractProtocolValidator;
import org.eclipse.paho.client.mqttv3.MqttTopic;

import akka.stream.alpakka.mqtt.MqttQoS;

/**
 * Connection specification for Mqtt protocol.
 */
@Immutable
public final class MqttValidator extends AbstractProtocolValidator {

    private static final String INVALID_TOPIC_FORMAT = "The provided topic ''{0}'' is not valid: {1}";
    private static final String QOS = "qos";

    private static final Collection<String> ACCEPTED_SCHEMES =
            Collections.unmodifiableList(Arrays.asList("tcp", "ssl", "ws", "wss"));


    /**
     * Create a new {@code MqttConnectionSpec}.
     *
     * @return a new instance.
     */
    public static MqttValidator newInstance() {
        return new MqttValidator();
    }

    @Override
    public ConnectionType type() {
        return ConnectionType.MQTT;
    }

    @Override
    public void validate(final Connection connection, final DittoHeaders dittoHeaders) {
        validateUriScheme(connection, dittoHeaders, ACCEPTED_SCHEMES, "MQTT 3.1.1");
        validateAddresses(connection, dittoHeaders);
        validateSourceConfigs(connection, dittoHeaders);
        validateTargetConfigs(connection, dittoHeaders);
    }


    @Override
    protected void validateSource(final Source source, final DittoHeaders dittoHeaders,
            final Supplier<String> sourceDescription) {
        if (!(source instanceof MqttSource)) {
            final String message =
                    MessageFormat.format("Source {0} must be of type MqttSource.", sourceDescription.get());
            throw ConnectionConfigurationInvalidException.newBuilder(message)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }

        final MqttSource mqttSource = (MqttSource) source;
        validateSourceQoS(mqttSource.getQos(), dittoHeaders, sourceDescription);
        validateSourceFilters(mqttSource.getFilters(), dittoHeaders, sourceDescription);
    }

    @Override
    protected void validateTarget(final Target target, final DittoHeaders dittoHeaders,
            final Supplier<String> targetDescription) {
        if (!(target instanceof MqttTarget)) {
            final String message =
                    MessageFormat.format("Target {0} must be of type MqttTarget.", targetDescription.get());
            throw ConnectionConfigurationInvalidException.newBuilder(message)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }

        final org.eclipse.ditto.model.connectivity.MqttTarget mqttTarget =
                (org.eclipse.ditto.model.connectivity.MqttTarget) target;
        validateTargetQoS(mqttTarget.getQos(), dittoHeaders, targetDescription);
    }

    /**
     * Retrieve quality of service from a validated specific config with "at-most-once" as default.
     *
     * @param qos th configured qos value.
     * @return quality of service.
     */
    static MqttQoS getQoS(final int qos) {
        switch (qos) {
            case 1:
                return MqttQoS.atLeastOnce();
            case 2:
                return MqttQoS.exactlyOnce();
            default:
                return MqttQoS.atMostOnce();
        }
    }

    private static void validateSourceFilters(final Collection<String> filters,
            final DittoHeaders dittoHeaders,
            final Supplier<String> sourceDescription) {

        final String dummyThingId = "namespace:name";
        final PlaceholderFilter placeholderFilter = new PlaceholderFilter();
        final Map<String, String> filtersMap = placeholderFilter.filterAddressesAsMap(filters, dummyThingId, filter -> {
            throw invalidValueForConfig(filter, "filters", sourceDescription.get())
                    .description("Placeholder substitution failed. " +
                            "Please check the placeholder variables against the documentation.")
                    .dittoHeaders(dittoHeaders)
                    .build();
        });
        filtersMap.forEach((filter, mqttTopic) ->
                validateMqttTopic(mqttTopic, true, errorMessage ->
                        invalidValueForConfig(filter, "filters", sourceDescription.get())
                                .description("The filter is not a valid MQTT topic after placeholder substitution. " +
                                        "Wildcard characters are allowed.")
                                .dittoHeaders(dittoHeaders)
                                .build()));
    }

    /*
     * MQTT Source does not support exactly-once delivery.
     */
    private static void validateSourceQoS(final int qos,
            final DittoHeaders dittoHeaders,
            final Supplier<String> errorSiteDescription) {

        validateQoS(qos, dittoHeaders, errorSiteDescription, i -> 0 <= i && i <= 2);
    }

    /*
     * MQTT Sink supports quality-of-service 0, 1, 2.
     */
    private static void validateTargetQoS(final int qos,
            final DittoHeaders dittoHeaders,
            final Supplier<String> errorSiteDescription) {

        validateQoS(qos, dittoHeaders, errorSiteDescription, i -> 0 <= i && i <= 2);
    }

    private static void validateQoS(final int qos, final DittoHeaders dittoHeaders,
            final Supplier<String> errorSiteDescription, final Predicate<Integer> predicate) {

        if (!predicate.test(qos)) {
            throw invalidValueForConfig(qos, QOS, errorSiteDescription.get())
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    private static void validateAddresses(final Connection connection, final DittoHeaders dittoHeaders) {
        connection.getSources()
                .stream()
                .flatMap(source -> source.getAddresses().stream())
                .forEach(a -> validateAddress(a, true, dittoHeaders));
        // no wildcards allowed for publish targets
        connection.getTargets()
                .stream()
                .map(Target::getAddress)
                .forEach(a -> validateAddress(a, false, dittoHeaders));
    }

    private static void validateAddress(final String address, final boolean wildcardAllowed,
            final DittoHeaders dittoHeaders) {
        validateMqttTopic(address, wildcardAllowed, errorMessage -> {
            final String message = MessageFormat.format(INVALID_TOPIC_FORMAT, address, errorMessage);
            return ConnectionConfigurationInvalidException.newBuilder(message)
                    .dittoHeaders(dittoHeaders)
                    .build();
        });
    }

    private static void validateMqttTopic(final String address, final boolean wildcardAllowed,
            final Function<String, DittoRuntimeException> errorProducer) {
        try {
            MqttTopic.validate(address, wildcardAllowed);
        } catch (final IllegalArgumentException e) {
            throw errorProducer.apply(e.getMessage());
        }

    }

    private static ConnectionConfigurationInvalidException.Builder invalidValueForConfig(final Object value,
            final String configName,
            final String location) {

        final String message = MessageFormat.format("Invalid value ''{0}'' for configuration ''{1}'' in {2}",
                value, configName, location);
        return ConnectionConfigurationInvalidException.newBuilder(message);
    }
}
