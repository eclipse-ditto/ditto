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

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.HonoAddressAlias;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.model.Target;
import org.eclipse.ditto.connectivity.model.Topic;
import org.eclipse.ditto.connectivity.model.UserPasswordCredentials;
import org.eclipse.ditto.connectivity.service.config.DefaultHonoConfig;
import org.eclipse.ditto.connectivity.service.config.HonoConfig;
import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import com.typesafe.config.ConfigFactory;

@RunWith(MockitoJUnitRunner.class)
public class HonoConnectionFactoryTest {

    private static final String TEST_USERNAME = "test-username";
    private static final String TEST_PASSWORD = "test-password";
    private static final String TEST_TENANT_ID = "test-tenant-id";
    @Rule
    public final ActorSystemResource actorSystemResource=
            ActorSystemResource.newInstance(ConfigFactory.load("test"));
    private Connection testConnection;

    private HonoConnectionFactory underTest;

    @Before
    public void setup() {
        honoConfig = new DefaultHonoConfig(actorSystemResource.getActorSystem());
        testConnection = ConnectivityModelFactory.connectionFromJson(getResource("test-connection.json"));
    }
    public final UserPasswordCredentials testCredentials =
            UserPasswordCredentials.newInstance(TEST_USERNAME, TEST_PASSWORD);


    private HonoConfig honoConfig;

    @Test
    public void testEnrichConnectionWithoutTenantId() {
        verifyConnection(true);
    }

    @Test
    public void testEnrichConnectionWithTenantId() {
        verifyConnection(false);
    }

    private void verifyConnection(boolean withTenantId) {
        underTest = getInstance(withTenantId);
        var expected = enrichTestConnection();
        assertEquals(expected, underTest.enrichConnection());
    }

    private HonoConnectionFactory getInstance(boolean withTenantId) {
        if (withTenantId) {
            return new HonoConnectionFactory(actorSystemResource.getActorSystem(), testConnection) {
                @Override
                protected UserPasswordCredentials getCredentials() {
                    return testCredentials;
                }

                @Override
                protected String getTenantId() {
                    return TEST_TENANT_ID;
                }
            };
        } else {
            return new HonoConnectionFactory(actorSystemResource.getActorSystem(), testConnection) {
                @Override
                protected UserPasswordCredentials getCredentials() {
                    return testCredentials;
                }

                @Override
                protected String getTenantId() {
                    return "";
                }
            };
        }
    }
    private static JsonObject getResource(final String fileName) {
        try (var resourceStream = HonoConnectionFactoryTest.class.getClassLoader().getResourceAsStream(fileName)) {
            assert resourceStream != null;
            return JsonObject.of(new String(resourceStream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("Test resource not found: " + fileName);
        }
    }

    private static String getBootstrapServerUrisAsCommaSeparatedListString(final HonoConfig honoConfig) {
        return honoConfig.getBootstrapServerUris()
                .stream()
                .map(URI::toString)
                .collect(Collectors.joining(","));
    }

    public Connection enrichTestConnection() {
        final var connectionId = testConnection.getId();
        return ConnectivityModelFactory.newConnectionBuilder(testConnection)
                .uri(honoConfig.getBaseUri().toString())
                .validateCertificate(honoConfig.isValidateCertificates())
                .specificConfig(Map.of(
                        "saslMechanism", honoConfig.getSaslMechanism().toString(),
                        "bootstrapServers", getBootstrapServerUrisAsCommaSeparatedListString(honoConfig),
                        "groupId", (underTest.getTenantId().isEmpty() ? "" : underTest.getTenantId() + "_") + connectionId)
                )
                .credentials(underTest.getCredentials())
                .setSources(testConnection.getSources()
                        .stream()
                        .map(source -> resolveSourceAliases(source, underTest.getTenantId()))
                        .toList())
                .setTargets(testConnection.getTargets()
                        .stream()
                        .map(target -> resolveTargetAlias(target, underTest.getTenantId()))
                        .toList())
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Source resolveSourceAliases(final Source source, final String tenantId) {
        final var sourceBuilder = ConnectivityModelFactory.newSourceBuilder(source)
                .addresses(source.getAddresses()
                        .stream()
                        .map(HonoAddressAlias::forAliasValue)
                        .flatMap(Optional::stream)
                        .map(honoAddressAlias -> honoAddressAlias.resolveAddress(tenantId))
                        .collect(Collectors.toSet()));
        if (source.getAddresses().contains(HonoAddressAlias.TELEMETRY.getAliasValue())
                || source.getAddresses().contains(HonoAddressAlias.EVENT.getAliasValue())) {
            source.getReplyTarget().ifPresent(replyTarget -> {
                var headerMapping = replyTarget.getHeaderMapping().toJson()
                        .setValue("correlation-id", "{{ header:correlation-id }}");
                if (HonoAddressAlias.COMMAND.getAliasValue().equals(replyTarget.getAddress())) {
                    headerMapping = headerMapping
                            .setValue("device_id", "{{ thing:id }}")
                            .setValue("subject",
                                    "{{ header:subject | fn:default(topic:action-subject) | fn:default(topic:criterion) }}-response");
                }
                sourceBuilder.replyTarget(replyTarget.toBuilder()
                        .address(HonoAddressAlias.forAliasValue(replyTarget.getAddress())
                                .map(honoAddressAlias -> honoAddressAlias.resolveAddressWithThingIdSuffix(tenantId))
                                .orElseGet(replyTarget::getAddress))
                        .headerMapping(ConnectivityModelFactory.newHeaderMapping(headerMapping))
                        .build());
            });
        }
        if (source.getAddresses().contains(HonoAddressAlias.COMMAND_RESPONSE.getAliasValue())) {
            sourceBuilder.headerMapping(ConnectivityModelFactory
                    .newHeaderMapping(source.getHeaderMapping().toJson()
                            .setValue("correlation-id", "{{ header:correlation-id }}")
                            .setValue("status", "{{ header:status }}")));
        }
        return sourceBuilder.build();
    }

    private static Target resolveTargetAlias(final Target target, final String tenantId) {
        final var resolvedAddress = HonoAddressAlias.forAliasValue(target.getAddress())
                .map(honoAddressAlias -> honoAddressAlias.resolveAddressWithThingIdSuffix(tenantId))
                .orElseGet(target::getAddress);
        final var targetBuilder = ConnectivityModelFactory.newTargetBuilder(target);
        if (!resolvedAddress.isEmpty()) {
            targetBuilder.address(resolvedAddress);
        }

        var headerMapping = target.getHeaderMapping().toJson()
                .setValue("device_id", "{{ thing:id }}")
                .setValue("correlation-id", "{{ header:correlation-id }}")
                .setValue("subject", "{{ header:subject | fn:default(topic:action-subject) }}");
        if (target.getTopics().stream()
                .anyMatch(topic -> topic.getTopic() == Topic.LIVE_MESSAGES ||
                        topic.getTopic() == Topic.LIVE_COMMANDS)) {
            headerMapping = headerMapping.setValue("response-required", "{{ header:response-required }}");
        }
        targetBuilder.headerMapping(ConnectivityModelFactory.newHeaderMapping(headerMapping));
        return targetBuilder.build();
    }

}