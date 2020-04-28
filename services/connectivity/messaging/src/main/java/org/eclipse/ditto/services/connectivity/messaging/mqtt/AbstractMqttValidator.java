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
package org.eclipse.ditto.services.connectivity.messaging.mqtt;

import static org.eclipse.ditto.model.placeholders.PlaceholderFactory.newHeadersPlaceholder;
import static org.eclipse.ditto.model.placeholders.PlaceholderFactory.newThingPlaceholder;
import static org.eclipse.ditto.model.placeholders.PlaceholderFactory.newTopicPathPlaceholder;

import java.text.MessageFormat;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectivityModelFactory;
import org.eclipse.ditto.model.connectivity.Enforcement;
import org.eclipse.ditto.model.connectivity.EnforcementFactoryFactory;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.SourceAddressPlaceholder;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.model.placeholders.ExpressionResolver;
import org.eclipse.ditto.model.placeholders.Placeholder;
import org.eclipse.ditto.model.placeholders.PlaceholderFactory;
import org.eclipse.ditto.model.placeholders.PlaceholderFilter;
import org.eclipse.ditto.model.placeholders.ThingPlaceholder;
import org.eclipse.ditto.model.placeholders.UnresolvedPlaceholderException;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.connectivity.messaging.validation.AbstractProtocolValidator;

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
    protected static final Collection<String> SECURE_SCHEMES = Collections.unmodifiableList(
            Collections.singletonList("ssl"));

    private static final String ERROR_DESCRIPTION = "''{0}'' is not a valid value for MQTT enforcement. Valid" +
            " values are: ''{1}''.";

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
        validateTemplate(target.getAddress(), dittoHeaders, newThingPlaceholder(), newTopicPathPlaceholder(),
                newHeadersPlaceholder());
    }

    /**
     * Retrieve quality of service from a validated specific config with "at-most-once" as default.
     *
     * @param qos th configured qos value.
     * @return quality of service.
     */
    public static MqttQos getHiveQoS(final int qos) {
        switch (qos) {
            case 1:
                return MqttQos.AT_LEAST_ONCE;
            case 2:
                return MqttQos.EXACTLY_ONCE;
            default:
                return MqttQos.AT_MOST_ONCE;
        }
    }

    protected static void validateSourceEnforcement(@Nullable final Enforcement enforcement,
            final DittoHeaders dittoHeaders, final Supplier<String> sourceDescription) {
        if (enforcement != null) {

            validateEnforcementInput(enforcement, sourceDescription, dittoHeaders);

            final ThingId dummyThingId = ThingId.of("namespace", "name");
            final Map<String, String> filtersMap =
                    applyEntityPlaceholderToAddresses(enforcement.getFilters(),
                            dummyThingId, filter -> {
                                throw invalidValueForConfig(filter, "filters", sourceDescription.get())
                                        .description("Placeholder substitution failed. " +
                                                "Please check the placeholder variables against the documentation.")
                                        .dittoHeaders(dittoHeaders)
                                        .build();
                            });
            filtersMap.forEach((filter, mqttTopic) ->
                    validateMqttTopic(mqttTopic, true, errorMessage ->
                            invalidValueForConfig(filter, "filters", sourceDescription.get())
                                    .description(
                                            "The filter is not a valid MQTT topic after placeholder substitution. " +
                                                    "Wildcard characters are allowed.")
                                    .dittoHeaders(dittoHeaders)
                                    .build()));
        }
    }

    protected static void validateEnforcementInput(final Enforcement enforcement,
            final Supplier<String> sourceDescription, final DittoHeaders dittoHeaders) {
        final SourceAddressPlaceholder sourceAddressPlaceholder =
                ConnectivityModelFactory.newSourceAddressPlaceholder();
        try {
            EnforcementFactoryFactory
                    .newEnforcementFilterFactory(enforcement, sourceAddressPlaceholder)
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
                value, configName, location);
        return ConnectionConfigurationInvalidException.newBuilder(message);
    }

    /**
     * Apply {@link ThingPlaceholder}s to addresses and collect the result as a map.
     *
     * @param addresses addresses to apply placeholder substitution.
     * @param entityId the entity ID.
     * @param unresolvedPlaceholderListener what to do if placeholder substitution fails.
     * @return map from successfully filtered addresses to the result of placeholder substitution.
     * @throws UnresolvedPlaceholderException if not all placeholders could be resolved
     */
    private static <T extends EntityId> Map<String, String> applyEntityPlaceholderToAddresses(
            final Collection<String> addresses,
            final T entityId,
            final Consumer<String> unresolvedPlaceholderListener) {

        return addresses.stream()
                .flatMap(address -> {
                    final String filteredAddress =
                            applyEntityPlaceholder(address, entityId, unresolvedPlaceholderListener);
                    return filteredAddress == null
                            ? Stream.empty()
                            : Stream.of(new AbstractMap.SimpleEntry<>(address, filteredAddress));
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Nullable
    private static <T extends EntityId> String applyEntityPlaceholder(final String address, final T entityId,
            final Consumer<String> unresolvedPlaceholderListener) {

        final List<Placeholder<CharSequence>> placeholders = Arrays.asList(
                PlaceholderFactory.newThingPlaceholder(),
                PlaceholderFactory.newPolicyPlaceholder(),
                PlaceholderFactory.newEntityPlaceholder()
        );

        for (final Placeholder<CharSequence> placeholder : placeholders) {
            try {
                final ExpressionResolver expressionResolver =
                        PlaceholderFactory.newExpressionResolver(placeholder, entityId);
                return PlaceholderFilter.apply(address, expressionResolver);
            } catch (final UnresolvedPlaceholderException e) {
                // do nothing, next placeholder will be executed, unresolved placeholder will be reported if none of
                // the placeholders can be applied successfully
            }
        }

        unresolvedPlaceholderListener.accept(address);
        return null;
    }
}
