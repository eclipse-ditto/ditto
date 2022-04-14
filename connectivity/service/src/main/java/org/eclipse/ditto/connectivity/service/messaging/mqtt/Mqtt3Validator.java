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
package org.eclipse.ditto.connectivity.service.messaging.mqtt;

import java.util.List;
import java.util.function.Supplier;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.common.Placeholders;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.HeaderMapping;
import org.eclipse.ditto.connectivity.model.ReplyTarget;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.config.MqttConfig;
import org.eclipse.ditto.placeholders.HeadersPlaceholder;
import org.eclipse.ditto.placeholders.Placeholder;
import org.eclipse.ditto.placeholders.PlaceholderFactory;

import akka.actor.ActorSystem;

/**
 * Connection specification for Mqtt 3.1.1 protocol.
 */
@Immutable
public final class Mqtt3Validator extends AbstractMqttValidator {

    private Mqtt3Validator(final MqttConfig mqttConfig) {
        super(mqttConfig);
    }

    private static final HeadersPlaceholder HEADERS_PLACEHOLDER = PlaceholderFactory.newHeadersPlaceholder();

    /**
     * Create a new {@code Mqtt3Validator}.
     *
     * @param mqttConfig used to create the fallback specific config
     * @return a new instance.
     */
    public static Mqtt3Validator newInstance(final MqttConfig mqttConfig) {
        return new Mqtt3Validator(mqttConfig);
    }

    @Override
    public ConnectionType type() {
        return ConnectionType.MQTT;
    }

    @Override
    public void validate(final Connection connection, final DittoHeaders dittoHeaders, final ActorSystem actorSystem,
            final ConnectivityConfig connectivityConfig) {
        validateUriScheme(connection, dittoHeaders, ACCEPTED_SCHEMES, SECURE_SCHEMES, "MQTT 3.1.1");
        validateClientCount(connection, dittoHeaders);
        validateAddresses(connection, dittoHeaders);
        validateSourceConfigs(connection, dittoHeaders);
        validateTargetConfigs(connection, dittoHeaders);
        validatePayloadMappings(connection, actorSystem, connectivityConfig, dittoHeaders);
        validateSpecificConfig(connection, dittoHeaders);
    }

    private static void validateClientCount(final Connection connection, final DittoHeaders dittoHeaders) {
        if (connection.getClientCount() > 1) {
            throw ConnectionConfigurationInvalidException
                    .newBuilder("Client count limited to 1 for MQTT 3.1.1 connections.")
                    .description("MQTT 3.1.1 does not support load-balancing; starting more than 1 client will only " +
                            "result in duplicate incoming messages.")
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    @Override
    protected void validateSource(final Source source, final DittoHeaders dittoHeaders,
            final Supplier<String> sourceDescription) {
        super.validateSource(source, dittoHeaders, sourceDescription);

        if (containsMappings(source.getHeaderMapping()) && !sourceContainsOnlyAllowedMappings(source, dittoHeaders)) {
            throw ConnectionConfigurationInvalidException.newBuilder(
                            "Header mapping is not supported for MQTT 3.1.1 sources.")
                    .dittoHeaders(dittoHeaders).build();
        }

        source.getReplyTarget()
                .map(ReplyTarget::getHeaderMapping)
                .ifPresent(headerMapping -> containsOnlyAllowedTargetMappings(headerMapping, dittoHeaders));

        validateConsumerCount(source, dittoHeaders);
    }

    private boolean sourceContainsOnlyAllowedMappings(final Source source,
            final DittoHeaders dittoHeaders) {
        source.getHeaderMapping().getMapping()
                .forEach((key, value) -> {
                    // constant header mappings are allowed and are not checked further
                    if (Placeholders.containsAnyPlaceholder(value)) {
                        // allow only a fixed set of placeholders
                        validateTemplate(value, dittoHeaders, Mqtt3HeaderPlaceholder.INSTANCE);
                    }
                });
        return true;
    }

    @Override
    protected void validateTarget(final Target target, final DittoHeaders dittoHeaders,
            final Supplier<String> targetDescription) {
        super.validateTarget(target, dittoHeaders, targetDescription);

        if (containsMappings(target.getHeaderMapping())
                && !targetContainsOnlyAllowedHeaderMappings(target, dittoHeaders)) {
            throw ConnectionConfigurationInvalidException.newBuilder(
                            "Header mapping is not supported for MQTT 3.1.1 targets.")
                    .dittoHeaders(dittoHeaders).build();
        }
    }

    private static boolean containsMappings(final HeaderMapping headerMapping) {
        return !headerMapping.getMapping().isEmpty();
    }

    private boolean targetContainsOnlyAllowedHeaderMappings(final Target target, final DittoHeaders dittoHeaders) {
        return containsOnlyAllowedTargetMappings(target.getHeaderMapping(), dittoHeaders);
    }

    private boolean containsOnlyAllowedTargetMappings(final HeaderMapping headerMapping,
            final DittoHeaders dittoHeaders) {
        headerMapping.getMapping().forEach((key, value) -> {
            // only fixed set of keys are allowed (see MqttHeader#getHeaderNames())
            checkIfKeyIsAllowed(key, dittoHeaders);
            if (Placeholders.containsAnyPlaceholder(value)) {
                // allow any header placeholder as value
                validateTemplate(value, dittoHeaders, HEADERS_PLACEHOLDER);
            }
        });
        return true;
    }

    private static void checkIfKeyIsAllowed(final String key, final DittoHeaders dittoHeaders) {
        if (!MqttHeader.getHeaderNames().contains(key)) {
            final String message = String.format("The header '%s' is not allowed in MQTT 3.1.1 target header mapping.",
                    key);
            final String description = String.format(
                    "The following headers are allowed and are directly applied to the published MQTT message: %s",
                    MqttHeader.getHeaderNames());
            throw ConnectionConfigurationInvalidException
                    .newBuilder(message)
                    .description(description)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    private static void validateConsumerCount(final Source source, final DittoHeaders dittoHeaders) {
        if (source.getConsumerCount() > 1) {
            throw ConnectionConfigurationInvalidException
                    .newBuilder("Consumer count limited to 1 for MQTT 3.1.1 connections.")
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    /**
     * Validates header mappings of MQTT 3 sources which only allow a fixed set of headers/properties.
     */
    private static final class Mqtt3HeaderPlaceholder implements Placeholder<String> {

        private static final Mqtt3HeaderPlaceholder INSTANCE = new Mqtt3HeaderPlaceholder();

        private Mqtt3HeaderPlaceholder() {
        }

        @Override
        public List<String> resolveValues(final String placeholderSource, final String name) {
            throw new UnsupportedOperationException("This placeholder is only used for validation and does not " +
                    "resolve the placeholder.");
        }

        @Override
        public String getPrefix() {
            return "header";
        }

        @Override
        public List<String> getSupportedNames() {
            return MqttHeader.getHeaderNames();
        }

        @Override
        public boolean supports(final String name) {
            return MqttHeader.getHeaderNames().contains(name);
        }
    }
}
