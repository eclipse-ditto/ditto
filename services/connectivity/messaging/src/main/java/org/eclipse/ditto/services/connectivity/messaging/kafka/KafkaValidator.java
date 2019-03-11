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
package org.eclipse.ditto.services.connectivity.messaging.kafka;

import static org.eclipse.ditto.model.placeholders.PlaceholderFactory.newThingPlaceholder;
import static org.eclipse.ditto.model.placeholders.PlaceholderFactory.newTopicPathPlaceholder;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.connectivity.messaging.validation.AbstractProtocolValidator;

/**
 * TODO: implement fully
 * TODO: unit test
 * Connection specification for Kafka protocol.
 */
@Immutable
public final class KafkaValidator extends AbstractProtocolValidator {

    private static final String INVALID_TOPIC_FORMAT = "The provided topic ''{0}'' is not valid: {1}";

    private static final Collection<String> ACCEPTED_SCHEMES =
            Collections.unmodifiableList(Arrays.asList("tcp", "ssl"));

    /**
     * Create a new {@code MqttConnectionSpec}.
     *
     * @return a new instance.
     */
    public static KafkaValidator newInstance() {
        return new KafkaValidator();
    }

    @Override
    public ConnectionType type() {
        return ConnectionType.KAFKA;
    }

    @Override
    public void validate(final Connection connection, final DittoHeaders dittoHeaders) {
        validateUriScheme(connection, dittoHeaders, ACCEPTED_SCHEMES, "Kafka 2.1.1");
        validateAddresses(connection, dittoHeaders);
        validateTargetConfigs(connection, dittoHeaders);
    }


    @Override
    protected void validateSource(final Source source, final DittoHeaders dittoHeaders,
            final Supplier<String> sourceDescription) {
        final String message = "Kafka connectivity currently does not provide sources.";
        throw ConnectionConfigurationInvalidException.newBuilder(message)
                .dittoHeaders(dittoHeaders)
                .build();
    }

    @Override
    protected void validateTarget(final Target target, final DittoHeaders dittoHeaders,
            final Supplier<String> targetDescription) {
        validateTemplate(target.getAddress(), dittoHeaders, newThingPlaceholder(), newTopicPathPlaceholder());
    }

    private static void validateAddresses(final Connection connection, final DittoHeaders dittoHeaders) {
        // no wildcards allowed for publish targets
        connection.getTargets()
                .stream()
                .map(Target::getAddress)
                .forEach(a -> validateAddress(a, dittoHeaders));
    }

    private static void validateAddress(final String address, final DittoHeaders dittoHeaders) {
        validateKafkaTopic(address, errorMessage -> {
            final String message = MessageFormat.format(INVALID_TOPIC_FORMAT, address, errorMessage);
            return ConnectionConfigurationInvalidException.newBuilder(message)
                    .dittoHeaders(dittoHeaders)
                    .build();
        });
    }

    private static void validateKafkaTopic(final String address, final Function<String, DittoRuntimeException> errorProducer) {
        try {
            // TODO: implement
        } catch (final IllegalArgumentException e) {
            throw errorProducer.apply(e.getMessage());
        }

    }
}
