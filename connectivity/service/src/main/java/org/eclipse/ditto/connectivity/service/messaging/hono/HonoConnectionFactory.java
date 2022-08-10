/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.service.messaging.hono;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkArgument;
import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.net.URI;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.concurrent.NotThreadSafe;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.FilteredTopic;
import org.eclipse.ditto.connectivity.model.HeaderMapping;
import org.eclipse.ditto.connectivity.model.HonoAddressAlias;
import org.eclipse.ditto.connectivity.model.ReplyTarget;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.model.Topic;
import org.eclipse.ditto.connectivity.model.UserPasswordCredentials;
import org.eclipse.ditto.connectivity.service.config.HonoConfig;

/**
 * Base implementation of a factory for getting a Hono {@link Connection}.
 * The Connection this factory supplies is based on a provided Connection with adjustments of
 * <ul>
 *     <li>the base URI,</li>
 *     <li>the "validate certificates" flag,</li>
 *     <li>the specific config including SASL mechanism, bootstrap server URIs and group ID,</li>
 *     <li>the credentials and</li>
 *     <li>the sources and targets.</li>
 * </ul>
 *
 * @since 3.0.0
 */
public abstract class HonoConnectionFactory {

    private static final Map<String, HonoAddressAlias> HONO_ADDRESS_ALIASES_BY_ALIAS_VALUE =
            Stream.of(HonoAddressAlias.values())
                    .collect(Collectors.toUnmodifiableMap(HonoAddressAlias::getAliasValue, Function.identity()));

    protected final Connection connection;

    /**
     * Constructs a {@code HonoConnectionFactory}.
     *
     * @param connection the connection that serves as base for the Hono connection this factory returns.
     * @throws NullPointerException if {@code connection} is {@code null}.
     * @throws IllegalArgumentException if the type of {@code connection} is not {@link ConnectionType#HONO};
     */
    protected HonoConnectionFactory(final Connection connection) {
        this.connection = checkArgument(
                checkNotNull(connection, "connection"),
                arg -> ConnectionType.HONO == arg.getConnectionType(),
                () -> MessageFormat.format("Expected type of connection to be <{0}> but it was <{1}>.",
                        ConnectionType.HONO,
                        connection.getConnectionType())
        );
    }

    /**
     * Returns a proper Hono Connection for the Connection that was used to create this factory instance.
     *
     * @return the Hono Connection.
     */
    public Connection getHonoConnection() {
        return ConnectivityModelFactory.newConnectionBuilder(connection)
                .uri(String.valueOf(getBaseUri()))
                .validateCertificate(isValidateCertificates())
                .specificConfig(getSpecificConfig())
                .credentials(getCredentials())
                .setSources(getSources(connection.getSources()))
                .setTargets(getTargets(connection.getTargets()))
                .build();
    }

    protected abstract URI getBaseUri();

    protected abstract boolean isValidateCertificates();

    private Map<String, String> getSpecificConfig() {
        return Map.of(
                "saslMechanism", String.valueOf(getSaslMechanism()),
                "bootstrapServers", getAsCommaSeparatedListString(getBootstrapServerUris()),
                "groupId", getGroupId(connection)
        );
    }

    protected abstract HonoConfig.SaslMechanism getSaslMechanism();

    protected abstract Set<URI> getBootstrapServerUris();

    private static String getAsCommaSeparatedListString(final Collection<URI> uris) {
        return uris.stream()
                .map(URI::toString)
                .collect(Collectors.joining(","));
    }

    protected abstract String getGroupId(Connection connection);

    protected abstract UserPasswordCredentials getCredentials();

    private List<Source> getSources(final Collection<Source> originalSources) {
        return originalSources.stream()
                .map(originalSource -> ConnectivityModelFactory.newSourceBuilder(originalSource)
                        .addresses(resolveSourceAddresses(originalSource.getAddresses()))
                        .replyTarget(getReplyTargetForSource(originalSource).orElse(null))
                        .headerMapping(getSourceHeaderMapping(originalSource))
                        .build())
                .collect(Collectors.toList());
    }

    private Set<String> resolveSourceAddresses(final Collection<String> unresolvedSourceAddresses) {
        return unresolvedSourceAddresses.stream()
                .map(unresolvedSourceAddress -> getHonoAddressAliasByAliasValue(unresolvedSourceAddress)
                        .map(this::resolveSourceAddress)
                        .orElse(unresolvedSourceAddress))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
    
    private static Optional<HonoAddressAlias> getHonoAddressAliasByAliasValue(final String aliasValue) {
        return Optional.ofNullable(HONO_ADDRESS_ALIASES_BY_ALIAS_VALUE.get(aliasValue));
    }

    protected abstract String resolveSourceAddress(HonoAddressAlias honoAddressAlias);

    private Optional<ReplyTarget> getReplyTargetForSource(final Source source) {
        final Optional<ReplyTarget> result;
        if (isApplyReplyTarget(source.getAddresses())) {
            result = source.getReplyTarget()
                    .map(replyTarget -> replyTarget.toBuilder()
                            .address(resolveTargetAddressOrKeepUnresolved(replyTarget.getAddress()))
                            .headerMapping(getReplyTargetHeaderMapping(replyTarget))
                            .build());
        } else {
            result = Optional.empty();
        }
        return result;
    }

    private static boolean isApplyReplyTarget(final Collection<String> sourceAddresses) {
        final Predicate<String> isTelemetryHonoAddressAlias = HonoAddressAlias.TELEMETRY.getAliasValue()::equals;
        final Predicate<String> isEventHonoAddressAlias = HonoAddressAlias.EVENT.getAliasValue()::equals;

        return sourceAddresses.stream()
                .anyMatch(isTelemetryHonoAddressAlias.or(isEventHonoAddressAlias));
    }

    private String resolveTargetAddressOrKeepUnresolved(final String unresolvedTargetAddress) {
        return getHonoAddressAliasByAliasValue(unresolvedTargetAddress)
                .map(this::resolveTargetAddress)
                .orElse(unresolvedTargetAddress);
    }

    protected abstract String resolveTargetAddress(HonoAddressAlias honoAddressAlias);

    private static HeaderMapping getReplyTargetHeaderMapping(final ReplyTarget replyTarget) {
        final var headerMappingBuilder = HeaderMappingBuilder.of(replyTarget.getHeaderMapping());
        headerMappingBuilder.putCorrelationId();
        if (isCommandHonoAddressAlias(replyTarget.getAddress())) {
            headerMappingBuilder.putDeviceId();
            headerMappingBuilder.putSubject(
                    "{{ header:subject | fn:default(topic:action-subject) | fn:default(topic:criterion) }}-response"
            );
        }
        return headerMappingBuilder.build();
    }

    private static boolean isCommandHonoAddressAlias(final String replyTargetAddress) {
        return replyTargetAddress.equals(HonoAddressAlias.COMMAND.getAliasValue());
    }

    private static HeaderMapping getSourceHeaderMapping(final Source source) {
        final HeaderMapping result;
        if (isAdjustSourceHeaderMapping(source.getAddresses())) {
            result = HeaderMappingBuilder.of(source.getHeaderMapping())
                    .putCorrelationId()
                    .putEntry("status", "{{ header:status }}")
                    .build();
        } else {
            result = source.getHeaderMapping();
        }
        return result;
    }

    private static boolean isAdjustSourceHeaderMapping(final Collection<String> sourceAddresses) {
        return sourceAddresses.contains(HonoAddressAlias.COMMAND_RESPONSE.getAliasValue());
    }

    private List<Target> getTargets(final Collection<Target> originalTargets) {
        return originalTargets.stream()
                .map(originalTarget -> ConnectivityModelFactory.newTargetBuilder(originalTarget)
                        .address(resolveTargetAddressOrKeepUnresolved(originalTarget.getAddress()))
                        .headerMapping(getTargetHeaderMapping(originalTarget))
                        .build())
                .collect(Collectors.toList());
    }

    private static HeaderMapping getTargetHeaderMapping(final Target target) {
        final var headerMappingBuilder = HeaderMappingBuilder.of(target.getHeaderMapping())
                .putDeviceId()
                .putCorrelationId()
                .putSubject("{{ header:subject | fn:default(topic:action-subject) }}");

        if (isPutResponseRequiredHeaderMapping(target.getTopics())) {
            headerMappingBuilder.putEntry("response-required", "{{ header:response-required }}");
        }
        return headerMappingBuilder.build();
    }

    private static boolean isPutResponseRequiredHeaderMapping(final Collection<FilteredTopic> targetTopics) {
        final Predicate<Topic> isLiveMessages = topic -> Topic.LIVE_MESSAGES == topic;
        final Predicate<Topic> isLiveCommands = topic -> Topic.LIVE_COMMANDS == topic;

        return targetTopics.stream()
                .map(FilteredTopic::getTopic)
                .anyMatch(isLiveMessages.or(isLiveCommands));
    }

    @NotThreadSafe
    private static final class HeaderMappingBuilder {

        private final Map<String, String> headerMappingDefinition;

        private HeaderMappingBuilder(final HeaderMapping existingHeaderMapping) {
            headerMappingDefinition = new LinkedHashMap<>(existingHeaderMapping.getMapping());
        }

        static HeaderMappingBuilder of(final HeaderMapping existingHeaderMapping) {
            return new HeaderMappingBuilder(checkNotNull(existingHeaderMapping, "existingHeaderMapping"));
        }

        HeaderMappingBuilder putCorrelationId() {
            headerMappingDefinition.put("correlation-id", "{{ header:correlation-id }}");
            return this;
        }

        HeaderMappingBuilder putDeviceId() {
            headerMappingDefinition.put("device_id", "{{ thing:id }}");
            return this;
        }

        HeaderMappingBuilder putSubject(final String subjectValue) {
            headerMappingDefinition.put("subject", subjectValue);
            return this;
        }

        HeaderMappingBuilder putEntry(final String key, final String value) {
            headerMappingDefinition.put(key, value);
            return this;
        }

        HeaderMapping build() {
            return ConnectivityModelFactory.newHeaderMapping(headerMappingDefinition);
        }

    }

}
