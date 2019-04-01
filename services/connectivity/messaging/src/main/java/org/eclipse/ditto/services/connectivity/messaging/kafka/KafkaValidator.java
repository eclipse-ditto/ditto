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
package org.eclipse.ditto.services.connectivity.messaging.kafka;

import static org.eclipse.ditto.model.placeholders.PlaceholderFactory.newHeadersPlaceholder;
import static org.eclipse.ditto.model.placeholders.PlaceholderFactory.newThingPlaceholder;
import static org.eclipse.ditto.model.placeholders.PlaceholderFactory.newTopicPathPlaceholder;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

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

    private static final String DUMMY_TOPIC = "replaced_placeholder";
    private static final String DUMMY_PARTITION = "3";

    private static final String INVALID_TOPIC_FORMAT = "The provided topic ''{0}'' is not valid: {1}";
    private static final String NOT_EMPTY_FORMAT = "The provided {0} in your target address may not be empty.";

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
        final String placeholderReplacement = UUID.randomUUID().toString();
        final String addressWithoutPlaceholders = validateTemplateAndReplace(target.getAddress(), dittoHeaders,
                placeholderReplacement, newThingPlaceholder(), newTopicPathPlaceholder(), newHeadersPlaceholder());

        this.validateAddress(addressWithoutPlaceholders, dittoHeaders, placeholderReplacement);
    }

    private void validateAddress(final String address, final DittoHeaders dittoHeaders, final String placeholderReplacement) {
        if (KafkaPublishTarget.containsKey(address)) {
            validateTargetAddressWithKey(address, dittoHeaders, placeholderReplacement);
        } else if (KafkaPublishTarget.containsPartition(address)) {
            validateTargetAddressWithPartition(address, dittoHeaders, placeholderReplacement);
        } else {
            validateTopic(address, dittoHeaders, placeholderReplacement);
        }
    }

    private void validateTargetAddressWithKey(final String targetAddress, final DittoHeaders dittoHeaders, final String placeholderReplacement) {
        final String[] split = targetAddress.split(KafkaPublishTarget.KEY_SEPARATOR, 2);
        validateTopic(split[0], dittoHeaders, placeholderReplacement);
        validateKey(split[1], dittoHeaders);
    }

    private void validateTargetAddressWithPartition(final String targetAddress,
            final DittoHeaders dittoHeaders, final String placeholderReplacement) {
        final String[] split = targetAddress.split(KafkaPublishTarget.PARTITION_SEPARATOR, 2);
        validateTopic(split[0], dittoHeaders, placeholderReplacement);
        validatePartition(split[1], dittoHeaders, placeholderReplacement);
    }

    private void validateTopic(final String topic, final DittoHeaders dittoHeaders, final String placeholderReplacement) {
        if (topic.isEmpty()) {
            throwEmptyException("topic", dittoHeaders);
        }

        try {
            final String topicWithoutPlaceholders = topic.replaceAll(Pattern.quote(placeholderReplacement), DUMMY_TOPIC);
            Topic.validate(topicWithoutPlaceholders);
        } catch (final InvalidTopicException e) {
            final String message = MessageFormat.format(INVALID_TOPIC_FORMAT, topic, e.getMessage());
            throw ConnectionConfigurationInvalidException.newBuilder(message)
                    .dittoHeaders(dittoHeaders)
                    .cause(e)
                    .build();
        }
    }

    private void validateKey(final String key, final DittoHeaders dittoHeaders) {
        if (key.isEmpty()) {
            throwEmptyException("key", dittoHeaders);
        }
    }

    private void validatePartition(final String partition, final DittoHeaders dittoHeaders, final String placeholderReplacement) {
        if (partition.isEmpty()) {
            throwEmptyException("partition", dittoHeaders);
        }
        try {
            final String partitionWithoutPlaceholders = partition.replaceAll(Pattern.quote(placeholderReplacement), DUMMY_PARTITION);
            Integer.parseInt(partitionWithoutPlaceholders);
        } catch (final NumberFormatException e) {
            final String message = MessageFormat.format("Can not parse partition number from {0}.",
                    partition.replaceAll(Pattern.quote(placeholderReplacement), "<placeholder>"));
            throw ConnectionConfigurationInvalidException.newBuilder(message)
                    .cause(e)
                    .build();
        }
    }

    private void validateSpecificConfigs(final Connection connection, final DittoHeaders dittoHeaders) {
        SPECIFIC_CONFIGS.stream()
                .filter(specificConfig -> specificConfig.isApplicable(connection))
                .forEach(specificConfig -> specificConfig.validateOrThrow(connection, dittoHeaders));
    }

    private static void throwEmptyException(final String type, final DittoHeaders dittoHeaders) {
        final String message = MessageFormat.format(NOT_EMPTY_FORMAT, type);
        throw ConnectionConfigurationInvalidException.newBuilder(message)
                .dittoHeaders(dittoHeaders)
                .build();
    }

}
