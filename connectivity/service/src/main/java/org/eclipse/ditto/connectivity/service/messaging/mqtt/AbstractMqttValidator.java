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

import static org.eclipse.ditto.connectivity.service.placeholders.ConnectivityPlaceholders.newEntityPlaceholder;
import static org.eclipse.ditto.connectivity.service.placeholders.ConnectivityPlaceholders.newPolicyPlaceholder;
import static org.eclipse.ditto.connectivity.service.placeholders.ConnectivityPlaceholders.newSourceAddressPlaceholder;
import static org.eclipse.ditto.connectivity.service.placeholders.ConnectivityPlaceholders.newThingPlaceholder;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.service.EnforcementFactoryFactory;
import org.eclipse.ditto.connectivity.service.placeholders.SourceAddressPlaceholder;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.Enforcement;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.service.config.MqttConfig;
import org.eclipse.ditto.connectivity.service.messaging.Resolvers;
import org.eclipse.ditto.connectivity.service.messaging.mqtt.hivemq.common.InvalidMqttQosCodeException;
import org.eclipse.ditto.connectivity.service.messaging.validation.AbstractProtocolValidator;
import org.eclipse.ditto.placeholders.PlaceholderFilter;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.datatypes.MqttTopic;
import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;

/**
 * Connection specification for Mqtt protocol.
 */
@Immutable
public abstract class AbstractMqttValidator extends AbstractProtocolValidator {

    private static final String INVALID_TOPIC_FORMAT = "The provided topic ''{0}'' is not valid: {1}";
    private static final String QOS = "qos";

    protected static final Collection<String> ACCEPTED_SCHEMES = List.of("tcp", "ssl");
    protected static final Collection<String> SECURE_SCHEMES = List.of("ssl");

    private static final String ERROR_DESCRIPTION =
            "''{0}'' is not a valid value for MQTT enforcement. Valid values are: ''{1}''.";

    private final MqttConfig mqttConfig;

    protected AbstractMqttValidator(final MqttConfig mqttConfig) {
        this.mqttConfig = mqttConfig;
    }

    @Override
    protected void validateSource(final Source source, final DittoHeaders dittoHeaders,
            final Supplier<String> sourceDescription) {
        final Optional<Integer> qos = source.getQos();
        if (qos.isEmpty()) {
            final String message =
                    MessageFormat.format("MQTT Source <{0}> must contain QoS value.", sourceDescription.get());
            throw ConnectionConfigurationInvalidException.newBuilder(message)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }

        validateSourceQoS(qos.get(), dittoHeaders, sourceDescription);
        validateSourceEnforcement(source.getEnforcement().orElse(null), dittoHeaders, sourceDescription);
    }

    @Override
    protected void validateTarget(final Target target, final DittoHeaders dittoHeaders,
            final Supplier<String> targetDescription) {
        final Optional<Integer> qos = target.getQos();
        if (qos.isEmpty()) {
            final String message =
                    MessageFormat.format("MQTT Target <{0}> must contain QoS value.", targetDescription.get());
            throw ConnectionConfigurationInvalidException.newBuilder(message)
                    .dittoHeaders(dittoHeaders)
                    .build();
        }

        validateTargetQoS(qos.get(), dittoHeaders, targetDescription);
        validateTemplate(target.getAddress(), dittoHeaders, Resolvers.getPlaceholders());
        validateExtraFields(target);
    }

    /**
     * Retrieve quality of service from a validated specific config with "at-most-once" as default.
     *
     * @param qos th configured qos value.
     * @return quality of service.
     */
    public static MqttQos getHiveQoS(final int qos) {
        return switch (qos) {
            case 1 -> MqttQos.AT_LEAST_ONCE;
            case 2 -> MqttQos.EXACTLY_ONCE;
            default -> MqttQos.AT_MOST_ONCE;
        };
    }

    protected static void validateSourceEnforcement(@Nullable final Enforcement enforcement,
            final DittoHeaders dittoHeaders, final Supplier<String> sourceDescription) {
        if (enforcement != null) {

            validateEnforcementInput(enforcement, sourceDescription, dittoHeaders);
            validateEnforcementFilters(enforcement.getFilters(), sourceDescription, dittoHeaders);
        }
    }

    protected static void validateEnforcementInput(final Enforcement enforcement,
            final Supplier<String> sourceDescription, final DittoHeaders dittoHeaders) {
        final SourceAddressPlaceholder sourceAddressPlaceholder = newSourceAddressPlaceholder();
        try {
            EnforcementFactoryFactory.newEnforcementFilterFactory(enforcement, sourceAddressPlaceholder)
                    .getFilter("dummyTopic");
        } catch (final DittoRuntimeException e) {
            throw invalidValueForConfig(enforcement.getInput(), "input", sourceDescription.get())
                    .cause(e)
                    .description(MessageFormat.format(ERROR_DESCRIPTION, enforcement.getInput(),
                            sourceAddressPlaceholder.getSupportedNames()))
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    /*
     * MQTT Source does not support exactly-once delivery.
     */
    protected static void validateSourceQoS(final int qos,
            final DittoHeaders dittoHeaders,
            final Supplier<String> errorSiteDescription) {

        validateQoS(qos, dittoHeaders, errorSiteDescription, i -> 0 <= i && i <= 2);
    }

    /*
     * MQTT Sink supports quality-of-service 0, 1, 2.
     */
    protected static void validateTargetQoS(final int qos,
            final DittoHeaders dittoHeaders,
            final Supplier<String> errorSiteDescription) {

        validateQoS(qos, dittoHeaders, errorSiteDescription, i -> 0 <= i && i <= 2);
    }

    private static void validateQoS(final int qos, final DittoHeaders dittoHeaders,
            final Supplier<String> errorSiteDescription, final IntPredicate predicate) {

        if (!predicate.test(qos)) {
            throw invalidValueForConfig(qos, QOS, errorSiteDescription.get())
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

    protected static void validateAddresses(final Connection connection, final DittoHeaders dittoHeaders) {
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

    protected void validateSpecificConfig(final Connection connection, final DittoHeaders dittoHeaders) {
        final var mqttSpecificConfig = MqttSpecificConfig.fromConnection(connection, mqttConfig);

        try {
            mqttSpecificConfig.getKeepAliveIntervalOrDefault();
        } catch (final IllegalKeepAliveIntervalSecondsException e) {
            throw ConnectionConfigurationInvalidException.newBuilder(e.getMessage())
                    .description("Please adjust the interval to be within the allowed range.")
                    .dittoHeaders(dittoHeaders)
                    .cause(e)
                    .build();
        }

        try {
            mqttSpecificConfig.getMqttLastWillTopic().ifPresent(unused -> mqttSpecificConfig.getLastWillQosOrThrow());
        } catch (final IllegalArgumentException e) {
            throw ConnectionConfigurationInvalidException.newBuilder(e.getMessage())
                    .description(MessageFormat.format("Please provide a valid MQTT topic for config key <{0}>.",
                            MqttSpecificConfig.LAST_WILL_TOPIC))
                    .dittoHeaders(dittoHeaders)
                    .cause(e)
                    .build();
        } catch (final InvalidMqttQosCodeException e) {
            throw ConnectionConfigurationInvalidException.newBuilder(e.getMessage())
                    .description(MessageFormat.format("Please provide a valid MQTT QoS code for config key <{0}>.",
                            MqttSpecificConfig.LAST_WILL_QOS))
                    .dittoHeaders(dittoHeaders)
                    .cause(e)
                    .build();
        }
    }

    private static void validateAddress(final String address,
            final boolean wildcardAllowed,
            final DittoHeaders dittoHeaders) {

        validateMqttTopic(address, wildcardAllowed, errorMessage -> {
            final String message = MessageFormat.format(INVALID_TOPIC_FORMAT, address, errorMessage);
            return ConnectionConfigurationInvalidException.newBuilder(message)
                    .dittoHeaders(dittoHeaders)
                    .build();
        });
    }

    private static void validateMqttTopic(final String address,
            final boolean wildcardAllowed,
            final Function<String, DittoRuntimeException> errorProducer) {

        try {
            if (wildcardAllowed) {
                // this one allows wildcard characters:
                MqttTopicFilter.of(address);
            } else {
                // this check doesn't allow wildcard characters:
                MqttTopic.of(address);
            }
        } catch (final IllegalArgumentException e) {
            throw errorProducer.apply(e.getMessage());
        }
    }

    private static ConnectionConfigurationInvalidException.Builder invalidValueForConfig(final Object value,
            final String configName,
            final String location) {

        final String message = MessageFormat.format("Invalid value ''{0}'' for configuration ''{1}'' in {2}",
                value,
                configName,
                location);
        return ConnectionConfigurationInvalidException.newBuilder(message);
    }

    /**
     * Validates that enforcementFilters are valid Strings or Placeholders.
     *
     * @param enforcementFilters enforcementFilters to apply placeholder substitution.
     */
    private static void validateEnforcementFilters(final Collection<String> enforcementFilters,
            final Supplier<String> sourceDescription,
            final DittoHeaders dittoHeaders) {

        try {
            enforcementFilters.forEach(
                    enforcementFilter -> PlaceholderFilter.validate(enforcementFilter,
                            newThingPlaceholder(),
                            newPolicyPlaceholder(),
                            newEntityPlaceholder())
            );
        } catch (final DittoRuntimeException dre) {
            throw invalidValueForConfig(enforcementFilters, "filters", sourceDescription.get())
                    .cause(dre)
                    .description("Placeholder substitution failed. Please check the placeholder variables against " +
                            "the documentation.")
                    .dittoHeaders(dittoHeaders)
                    .build();
        }
    }

}
