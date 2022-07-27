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

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.FilteredTopic;
import org.eclipse.ditto.connectivity.model.HeaderMapping;
import org.eclipse.ditto.connectivity.model.HonoAddressAlias;
import org.eclipse.ditto.connectivity.model.ReplyTarget;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.model.Topic;
import org.eclipse.ditto.connectivity.model.UserPasswordCredentials;
import org.eclipse.ditto.connectivity.service.config.DefaultHonoConfig;
import org.eclipse.ditto.connectivity.service.config.HonoConfig;
import org.eclipse.ditto.json.JsonFactory;

import akka.actor.ActorSystem;

public abstract class HonoConnectionFactory {

    protected final Connection connection;
    protected final HonoConfig honoConfig;

    protected HonoConnectionFactory(final ActorSystem actorSystem, final Connection connection) {
        this.connection = connection;
        honoConfig = new DefaultHonoConfig(actorSystem);
    }

    public Connection enrichConnection() {
        final var tenantId = getTenantId();
        return ConnectivityModelFactory.newConnectionBuilder(connection)
                .uri(honoConfig.getBaseUri().toString())
                .validateCertificate(honoConfig.isValidateCertificates())
                .specificConfig(Map.of(
                        "saslMechanism", honoConfig.getSaslMechanism().toString(),
                        "bootstrapServers", getBootstrapServerUrisAsCommaSeparatedListString(honoConfig),
                        "groupId", (tenantId.isEmpty() ? "" : tenantId + "_") + connection.getId())
                )
                .credentials(getCredentials())
                .setSources(getSources(connection.getSources(), tenantId))
                .setTargets(getTargets(connection.getTargets(), tenantId))
                .build();
    }

    protected abstract String getTenantId();

    private static String getBootstrapServerUrisAsCommaSeparatedListString(final HonoConfig honoConfig) {
        return honoConfig.getBootstrapServerUris()
                .stream()
                .map(URI::toString)
                .collect(Collectors.joining(","));
    }

    protected abstract UserPasswordCredentials getCredentials();

    private static List<Source> getSources(final Collection<Source> originalSources, final CharSequence tenantId) {
        return originalSources.stream()
                .map(originalSource -> resolveSourceAliases(originalSource, tenantId))
                .collect(Collectors.toList());
    }

    private static Source resolveSourceAliases(final Source source, final CharSequence tenantId) {
        return ConnectivityModelFactory.newSourceBuilder(source)
                .addresses(resolveSourceAddresses(source.getAddresses(), tenantId))
                .replyTarget(getReplyTarget(source, tenantId).orElse(null))
                .headerMapping(getSourceHeaderMapping(source))
                .build();
    }

    private static Set<String> resolveSourceAddresses(
            final Collection<String> unresolvedSourceAddresses,
            final CharSequence tenantId
    ) {
        return unresolvedSourceAddresses.stream()
                .map(unresolvedSourceAddress -> HonoAddressAlias.forAliasValue(unresolvedSourceAddress)
                        .map(honoAddressAlias -> honoAddressAlias.resolveAddress(tenantId))
                        .orElse(unresolvedSourceAddress))
                .collect(Collectors.toSet());
    }

    private static Optional<ReplyTarget> getReplyTarget(final Source source, final CharSequence tenantId) {
        final Optional<ReplyTarget> result;
        if (isApplyReplyTarget(source.getAddresses())) {
            result = source.getReplyTarget()
                    .map(replyTarget -> {
                        final var replyTargetBuilder = replyTarget.toBuilder();
                        replyTargetBuilder.address(resolveTargetAddress(replyTarget.getAddress(), tenantId));
                        replyTargetBuilder.headerMapping(getReplyTargetHeaderMapping(replyTarget));
                        return replyTargetBuilder.build();
                    });
        } else {
            result = Optional.empty();
        }
        return result;
    }

    private static boolean isApplyReplyTarget(final Collection<String> sourceAddresses) {
        final Predicate<String> isTelemetryHonoAddressAlias = HonoAddressAlias.TELEMETRY.getAliasValue()::equals;
        final Predicate<String> isEventHonoAddressAlias = HonoAddressAlias.EVENT.getAliasValue()::equals;

        return sourceAddresses.stream().anyMatch(isTelemetryHonoAddressAlias.or(isEventHonoAddressAlias));
    }

    private static String resolveTargetAddress(final String unresolvedTargetAddress, final CharSequence tenantId) {
        return HonoAddressAlias.forAliasValue(unresolvedTargetAddress)
                .map(honoAddressAlias -> honoAddressAlias.resolveAddressWithThingIdSuffix(tenantId))
                .orElse(unresolvedTargetAddress);
    }

    private static HeaderMapping getReplyTargetHeaderMapping(final ReplyTarget replyTarget) {
        final var originalHeaderMapping = replyTarget.getHeaderMapping();
        final var headerMappingBuilder = JsonFactory.newObjectBuilder(originalHeaderMapping.toJson())
                .set("correlation-id", "{{ header:correlation-id }}");
        if (isCommandHonoAddressAlias(replyTarget.getAddress())) {
            headerMappingBuilder.set("device_id", "{{ thing:id }}");
            headerMappingBuilder.set("subject",
                    "{{ header:subject | fn:default(topic:action-subject) | fn:default(topic:criterion) }}-response");
        }
        return ConnectivityModelFactory.newHeaderMapping(headerMappingBuilder.build());
    }

    private static boolean isCommandHonoAddressAlias(final String replyTargetAddress) {
        return Objects.equals(HonoAddressAlias.COMMAND.getAliasValue(), replyTargetAddress);
    }

    private static HeaderMapping getSourceHeaderMapping(final Source source) {
        final HeaderMapping result;
        final var originalHeaderMapping = source.getHeaderMapping();
        if (isAdjustSourceHeaderMapping(source.getAddresses())) {
            result = ConnectivityModelFactory.newHeaderMapping(
                    JsonFactory.newObjectBuilder(originalHeaderMapping.toJson())
                            .set("correlation-id", "{{ header:correlation-id }}")
                            .set("status", "{{ header:status }}")
                            .build()
            );
        } else {
            result = originalHeaderMapping;
        }
        return result;
    }

    private static boolean isAdjustSourceHeaderMapping(final Collection<String> sourceAddresses) {
        return sourceAddresses.contains(HonoAddressAlias.COMMAND_RESPONSE.getAliasValue());
    }

    private static List<Target> getTargets(final Collection<Target> originalTargets, final CharSequence tenantId) {
        return originalTargets.stream()
                .map(originalTarget -> resolveTargetAlias(originalTarget, tenantId))
                .collect(Collectors.toList());
    }

    private static Target resolveTargetAlias(final Target target, final CharSequence tenantId) {
        return ConnectivityModelFactory.newTargetBuilder(target)
                .address(resolveTargetAddress(target.getAddress(), tenantId))
                .headerMapping(getTargetHeaderMapping(target))
                .build();
    }

    private static HeaderMapping getTargetHeaderMapping(final Target target) {
        final var headerMappingBuilder = JsonFactory.newObjectBuilder(target.getHeaderMapping().toJson())
                .set("device_id", "{{ thing:id }}")
                .set("correlation-id", "{{ header:correlation-id }}")
                .set("subject", "{{ header:subject | fn:default(topic:action-subject) }}");
        if (isSetResponseRequiredHeader(target.getTopics())) {
            headerMappingBuilder.set("response-required", "{{ header:response-required }}");
        }
        return ConnectivityModelFactory.newHeaderMapping(headerMappingBuilder.build());
    }

    private static boolean isSetResponseRequiredHeader(final Collection<FilteredTopic> targetTopics) {
        final Predicate<Topic> isLiveMessages = topic -> Topic.LIVE_MESSAGES == topic;
        final Predicate<Topic> isLiveCommands = topic -> Topic.LIVE_COMMANDS == topic;

        return targetTopics.stream()
                .map(FilteredTopic::getTopic)
                .anyMatch(isLiveMessages.or(isLiveCommands));
    }

}
