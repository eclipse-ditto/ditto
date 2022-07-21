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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.HonoAddressAlias;
import org.eclipse.ditto.connectivity.model.ImmutableHeaderMapping;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.model.Topic;
import org.eclipse.ditto.connectivity.model.UserPasswordCredentials;
import org.eclipse.ditto.connectivity.service.config.DefaultHonoConfig;
import org.eclipse.ditto.connectivity.service.config.HonoConfig;
import org.eclipse.ditto.json.JsonCollectors;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

import akka.actor.ActorSystem;

public abstract class HonoConnectionFactory {

    protected final Connection connection;
    protected final HonoConfig honoConfig;

    protected HonoConnectionFactory(final ActorSystem actorSystem, final Connection connection) {
        this.connection = connection;
        honoConfig = new DefaultHonoConfig(actorSystem);
    }

    protected abstract UserPasswordCredentials getCredentials();

    protected abstract String getTenantId();

    public Connection enrichConnection() {
        final var tenantId = getTenantId();
        return ConnectivityModelFactory.newConnectionBuilder(connection.getId(),
                        connection.getConnectionType(),
                        connection.getConnectionStatus(),
                        honoConfig.getBaseUri().toString())
                .validateCertificate(honoConfig.isValidateCertificates())
                .specificConfig(Map.of(
                        "saslMechanism", honoConfig.getSaslMechanism().toString(),
                        "bootstrapServers", getBootstrapServerUrisAsCommaSeparatedListString(honoConfig),
                        "groupId", (tenantId.isEmpty() ? "" : tenantId + "_") + connection.getId())
                )
                .credentials(getCredentials())
                .sources(connection.getSources()
                        .stream()
                        .map(source -> ConnectivityModelFactory.sourceFromJson(
                                resolveSourceAliases(source, tenantId), 1))
                        .toList())
                .targets(connection.getTargets()
                        .stream()
                        .map(target -> ConnectivityModelFactory.targetFromJson(resolveTargetAlias(target, tenantId)))
                        .toList())
                .build();
    }

    private static String getBootstrapServerUrisAsCommaSeparatedListString(final HonoConfig honoConfig) {
        return honoConfig.getBootstrapServerUris()
                .stream()
                .map(URI::toString)
                .collect(Collectors.joining(","));
    }

    private static JsonObject resolveSourceAliases(final Source source, final CharSequence tenantId) {
        final var sourceBuilder = JsonFactory.newObjectBuilder(source.toJson())
                .set(Source.JsonFields.ADDRESSES, resolveSourceAddresses(source.getAddresses(), tenantId)
                        .map(JsonValue::of)
                        .collect(JsonCollectors.valuesToArray()));
        source.getReplyTarget().ifPresent(replyTarget -> {
            var headerMapping = replyTarget.getHeaderMapping().toJson()
                    .setValue("correlation-id", "{{ header:correlation-id }}");
            if (HonoAddressAlias.COMMAND.getAliasValue().equals(replyTarget.getAddress())) {
                headerMapping = headerMapping
                        .setValue("device_id", "{{ thing:id }}")
                        .setValue("subject",
                                "{{ header:subject | fn:default(topic:action-subject) | fn:default(topic:criterion) }}-response");
            }
            sourceBuilder.set("replyTarget", replyTarget.toBuilder()
                    .address(resolveTargetAddress(replyTarget.getAddress(), tenantId))
                    .headerMapping(ImmutableHeaderMapping.fromJson(headerMapping))
                    .build().toJson());
        });
        if (source.getAddresses().contains(HonoAddressAlias.COMMAND_RESPONSE.getAliasValue())) {
            sourceBuilder.set("headerMapping", source.getHeaderMapping().toJson()
                    .setValue("correlation-id", "{{ header:correlation-id }}")
                    .setValue("status", "{{ header:status }}"));
        }
        return sourceBuilder.build();
    }

    private static Stream<String> resolveSourceAddresses(
            final Set<String> unresolvedSourceAddresses,
            final CharSequence tenantId
    ) {
        return unresolvedSourceAddresses.stream()
                .map(unresolvedSourceAddress -> HonoAddressAlias.forAliasValue(unresolvedSourceAddress)
                        .map(honoAddressAlias -> honoAddressAlias.resolveAddress(tenantId))
                        .orElse(unresolvedSourceAddress));
    }

    private static String resolveTargetAddress(final String unresolvedTargetAddress, final CharSequence tenantId) {
        return HonoAddressAlias.forAliasValue(unresolvedTargetAddress)
                .map(honoAddressAlias -> honoAddressAlias.resolveAddressWithThingIdSuffix(tenantId))
                .orElse(unresolvedTargetAddress);
    }

    private static JsonObject resolveTargetAlias(final Target target, final CharSequence tenantId) {
        final var targetBuilder = JsonFactory.newObjectBuilder(target.toJson())
                .set(Target.JsonFields.ADDRESS, resolveTargetAddress(target.getAddress(), tenantId));
        var headerMapping = target.getHeaderMapping().toJson()
                .setValue("device_id", "{{ thing:id }}")
                .setValue("correlation-id", "{{ header:correlation-id }}")
                .setValue("subject", "{{ header:subject | fn:default(topic:action-subject) }}");
        if (target.getTopics().stream()
                .anyMatch(topic -> topic.getTopic() == Topic.LIVE_MESSAGES ||
                        topic.getTopic() == Topic.LIVE_COMMANDS)) {
            headerMapping = headerMapping.setValue("response-required", "{{ header:response-required }}");
        }
        targetBuilder.set("headerMapping", headerMapping);
        return targetBuilder.build();
    }

}
