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
package org.eclipse.ditto.connectivity.service.messaging.kafka;

import static org.eclipse.ditto.connectivity.service.placeholders.ConnectivityPlaceholders.newEntityPlaceholder;
import static org.eclipse.ditto.connectivity.service.placeholders.ConnectivityPlaceholders.newFeaturePlaceholder;
import static org.eclipse.ditto.connectivity.service.placeholders.ConnectivityPlaceholders.newPolicyPlaceholder;
import static org.eclipse.ditto.connectivity.service.placeholders.ConnectivityPlaceholders.newThingPlaceholder;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

import org.apache.kafka.common.errors.InvalidTopicException;
import org.apache.kafka.common.internals.Topic;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.messaging.Resolvers;
import org.eclipse.ditto.connectivity.service.messaging.validation.AbstractProtocolValidator;
import org.eclipse.ditto.placeholders.PlaceholderFactory;
import org.eclipse.ditto.placeholders.UnresolvedPlaceholderException;

import akka.actor.ActorSystem;

/**
 * Validator for Kafka connections.
 */
@Immutable
public final class KafkaValidator extends AbstractProtocolValidator {

    private static final String DUMMY_TOPIC = "replaced_placeholder";
    private static final String DUMMY_PARTITION = "3";

    private static final String INVALID_TOPIC_FORMAT = "The provided topic ''{0}'' is not valid: {1}";
    private static final String NOT_EMPTY_FORMAT = "The provided {0} in your target address may not be empty.";

    private static final Collection<String> ACCEPTED_SCHEMES = List.of("tcp", "ssl");
    private static final Collection<String> SECURE_SCHEMES = List.of("ssl");

    @Nullable private static KafkaValidator instance;

    private final Collection<KafkaSpecificConfig> specificConfigs;

    private KafkaValidator() {
        super();
        specificConfigs = List.of(KafkaAuthenticationSpecificConfig.getInstance(),
                KafkaBootstrapServerSpecificConfig.getInstance(),
                KafkaConsumerGroupSpecificConfig.getInstance(),
                KafkaConsumerOffsetResetSpecificConfig.getInstance());
    }

    /**
     * Returns an instance of the Kafka validator.
     *
     * @return the instance.
     */
    public static KafkaValidator getInstance() {
        KafkaValidator result = instance;
        if (null == result) {
            result = new KafkaValidator();
            instance = result;
        }
        return result;
    }

    @Override
    public ConnectionType type() {
        return ConnectionType.KAFKA;
    }

    @Override
    public void validate(final Connection connection,
            final DittoHeaders dittoHeaders,
            final ActorSystem actorSystem,
            final ConnectivityConfig connectivityConfig) {
        validateUriScheme(connection, dittoHeaders, ACCEPTED_SCHEMES, SECURE_SCHEMES, "Kafka 2.1.1");
        validateSourceConfigs(connection, dittoHeaders);
        validateTargetConfigs(connection, dittoHeaders);
        validatePayloadMappings(connection, actorSystem, connectivityConfig, dittoHeaders);
        validateSpecificConfigs(connection, dittoHeaders);
    }

    @Override
    protected void validateSource(final Source source, final DittoHeaders dittoHeaders,
            final Supplier<String> sourceDescription) {
        source.getEnforcement().ifPresent(enforcement -> {
            validateTemplate(enforcement.getInput(), dittoHeaders, PlaceholderFactory.newHeadersPlaceholder());
            enforcement.getFilters().forEach(filterTemplate ->
                    validateTemplate(filterTemplate, dittoHeaders, newThingPlaceholder(), newPolicyPlaceholder(),
                            newEntityPlaceholder(), newFeaturePlaceholder()));
        });
        validateHeaderMapping(source.getHeaderMapping(), dittoHeaders);

        final String placeholderReplacement = UUID.randomUUID().toString();
        source.getAddresses().forEach(
                address -> validateSourceAddress(address, dittoHeaders, placeholderReplacement));

        validateSourceQos(source, dittoHeaders);
    }

    @Override
    protected void validateTarget(final Target target, final DittoHeaders dittoHeaders,
            final Supplier<String> targetDescription) {

        final String placeholderReplacement = UUID.randomUUID().toString();
        final String addressWithoutPlaceholders = validateTemplateAndReplace(target.getAddress(), dittoHeaders,
                placeholderReplacement, Resolvers.getPlaceholders()).stream()
                .findFirst()
                .orElseThrow(() -> UnresolvedPlaceholderException.newBuilder(target.getAddress()).build());

        validateTargetAddress(addressWithoutPlaceholders, dittoHeaders, placeholderReplacement);
        validateHeaderMapping(target.getHeaderMapping(), dittoHeaders);
        validateExtraFields(target);
    }

    private static void validateSourceAddress(final String address, final DittoHeaders dittoHeaders,
            final String placeholderReplacement) {
        validateTopic(address, dittoHeaders, placeholderReplacement);
    }

    private static void validateSourceQos(final Source source, final DittoHeaders dittoHeaders) {
        source.getQos().ifPresent(qos -> {
            if (qos < 0 || qos > 1) {
                throw ConnectionConfigurationInvalidException
                        .newBuilder("Invalid 'qos' value for Kafka source, supported are: <0> or <1>. " +
                                "Configured 'qos' value was: <" + qos + ">"
                        )
                        .dittoHeaders(dittoHeaders)
                        .build();
            }
        });
    }

    private static void validateTargetAddress(final String address, final DittoHeaders dittoHeaders,
            final String placeholderReplacement) {

        if (KafkaPublishTarget.containsKey(address)) {
            validateTargetAddressWithKey(address, dittoHeaders, placeholderReplacement);
        } else if (KafkaPublishTarget.containsPartition(address)) {
            validateTargetAddressWithPartition(address, dittoHeaders, placeholderReplacement);
        } else {
            validateTopic(address, dittoHeaders, placeholderReplacement);
        }
    }

    private static void validateTargetAddressWithKey(final String targetAddress, final DittoHeaders dittoHeaders,
            final String placeholderReplacement) {

        final String[] split = targetAddress.split(KafkaPublishTarget.KEY_SEPARATOR, 2);
        validateTopic(split[0], dittoHeaders, placeholderReplacement);
        validateKey(split[1], dittoHeaders);
    }

    private static void validateTargetAddressWithPartition(final String targetAddress,
            final DittoHeaders dittoHeaders, final String placeholderReplacement) {

        final String[] split = targetAddress.split(KafkaPublishTarget.PARTITION_SEPARATOR, 2);
        validateTopic(split[0], dittoHeaders, placeholderReplacement);
        validatePartition(split[1], dittoHeaders, placeholderReplacement);
    }

    private static void validateTopic(final String topic, final DittoHeaders dittoHeaders,
            final String placeholderReplacement) {

        if (topic.isEmpty()) {
            throwEmptyException("topic", dittoHeaders);
        }

        try {
            final String topicWithoutPlaceholders =
                    topic.replaceAll(Pattern.quote(placeholderReplacement), DUMMY_TOPIC);
            Topic.validate(topicWithoutPlaceholders);
        } catch (final InvalidTopicException e) {
            final String message = MessageFormat.format(INVALID_TOPIC_FORMAT, topic, e.getMessage());
            throw ConnectionConfigurationInvalidException.newBuilder(message)
                    .dittoHeaders(dittoHeaders)
                    .cause(e)
                    .build();
        }
    }

    private static void validateKey(final String key, final DittoHeaders dittoHeaders) {
        if (key.isEmpty()) {
            throwEmptyException("key", dittoHeaders);
        }
    }

    private static void validatePartition(final String partition, final DittoHeaders dittoHeaders,
            final String placeholderReplacement) {

        if (partition.isEmpty()) {
            throwEmptyException("partition", dittoHeaders);
        }
        try {
            final String partitionWithoutPlaceholders =
                    partition.replaceAll(Pattern.quote(placeholderReplacement), DUMMY_PARTITION);
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
        for (final KafkaSpecificConfig specificConfig : specificConfigs) {
            if (specificConfig.isApplicable(connection)) {
                specificConfig.validateOrThrow(connection, dittoHeaders);
            }
        }
    }

    private static void throwEmptyException(final String type, final DittoHeaders dittoHeaders) {
        final String message = MessageFormat.format(NOT_EMPTY_FORMAT, type);
        throw ConnectionConfigurationInvalidException.newBuilder(message)
                .dittoHeaders(dittoHeaders)
                .build();
    }

}
