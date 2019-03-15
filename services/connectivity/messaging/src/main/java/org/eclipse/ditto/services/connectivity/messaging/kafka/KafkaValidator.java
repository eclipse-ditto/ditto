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

import static org.eclipse.ditto.model.placeholders.PlaceholderFactory.newHeadersPlaceholder;
import static org.eclipse.ditto.model.placeholders.PlaceholderFactory.newThingPlaceholder;
import static org.eclipse.ditto.model.placeholders.PlaceholderFactory.newTopicPathPlaceholder;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Supplier;

import javax.annotation.concurrent.Immutable;

import org.apache.kafka.common.errors.InvalidTopicException;
import org.apache.kafka.common.internals.Topic;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.connectivity.Connection;
import org.eclipse.ditto.model.connectivity.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.model.connectivity.ConnectionType;
import org.eclipse.ditto.model.connectivity.Source;
import org.eclipse.ditto.model.connectivity.Target;
import org.eclipse.ditto.services.connectivity.messaging.validation.AbstractProtocolValidator;

/**
 * Validator for Kafka connections.
 */
@Immutable
public final class KafkaValidator extends AbstractProtocolValidator {

    private static final String INVALID_TOPIC_FORMAT = "The provided topic ''{0}'' is not valid: {1}";

    private static final Collection<String> ACCEPTED_SCHEMES =
            Collections.unmodifiableList(Arrays.asList("tcp", "ssl"));

    private static final Collection<KafkaSpecificConfig> SPECIFIC_CONFIGS =
            Collections.unmodifiableList(Arrays.asList(KafkaAuthenticationSpecificConfig.getInstance(), KafkaBootstrapServerSpecificConfig.getInstance()));

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
        validateSourceConfigs(connection, dittoHeaders);
        validateTargetConfigs(connection, dittoHeaders);
        validateSpecificConfigs(connection, dittoHeaders);
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

        validateTemplate(target.getAddress(), dittoHeaders, newThingPlaceholder(), newTopicPathPlaceholder(),
                newHeadersPlaceholder());
    }

    private static void validateAddresses(final Connection connection, final DittoHeaders dittoHeaders) {
        // no wildcards allowed for publish targets
        connection.getTargets()
                .stream()
                .map(Target::getAddress)
                .forEach(a -> validateAddress(a, dittoHeaders));
    }

    private static void validateAddress(final String address, final DittoHeaders dittoHeaders) {
        if (containsKey(address)) {
            validateTargetAddressWithKey(address, dittoHeaders);
        } else if (containsPartition(address)) {
            validateTargetAddressWithPartition(address, dittoHeaders);
        } else {
            validateTopic(address, dittoHeaders);
        }
    }

    private static boolean containsKey(final String targetAddress) {
        final int index = targetAddress.indexOf(KafkaPublishTarget.KEY_SEPARATOR);
        return index > 0 && index < targetAddress.length();
    }

    private static void validateTargetAddressWithKey(final String targetAddress, final DittoHeaders dittoHeaders) {
        final String[] split = targetAddress.split(KafkaPublishTarget.KEY_SEPARATOR, 2);
        validateTopic(split[0], dittoHeaders);
        // won't validate the key since it might contain placeholders
    }

    private static boolean containsPartition(final String targetAddress) {
        final int index = targetAddress.indexOf(KafkaPublishTarget.PARTITION_SEPARATOR);
        return index > 0 && index < targetAddress.length();
    }

    private static void validateTargetAddressWithPartition(final String targetAddress,
            final DittoHeaders dittoHeaders) {
        final String[] split = targetAddress.split(KafkaPublishTarget.PARTITION_SEPARATOR, 2);
        validateTopic(split[0], dittoHeaders);
        // won't validate the partition since it might contain placeholders
    }

    private static void validateTopic(final String topic, final DittoHeaders dittoHeaders) {
        try {
            Topic.validate(topic);
        } catch (final InvalidTopicException e) {
            final String message = MessageFormat.format(INVALID_TOPIC_FORMAT, topic, e.getMessage());
            throw ConnectionConfigurationInvalidException.newBuilder(message)
                    .dittoHeaders(dittoHeaders)
                    .cause(e)
                    .build();
        }
    }

    private void validateSpecificConfigs(final Connection connection, final DittoHeaders dittoHeaders) {
        SPECIFIC_CONFIGS.stream()
                .filter(specificConfig -> specificConfig.isApplicable(connection))
                .forEach(specificConfig -> specificConfig.validateOrThrow(connection, dittoHeaders));
    }

}
