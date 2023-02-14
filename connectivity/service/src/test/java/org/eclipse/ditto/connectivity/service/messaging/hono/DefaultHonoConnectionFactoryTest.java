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

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.connectivity.model.HonoAddressAlias.COMMAND_RESPONSE;
import static org.eclipse.ditto.connectivity.model.HonoAddressAlias.EVENT;
import static org.eclipse.ditto.connectivity.model.HonoAddressAlias.TELEMETRY;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.HonoAddressAlias;
import org.eclipse.ditto.connectivity.model.ReplyTarget;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.service.config.DefaultHonoConfig;
import org.eclipse.ditto.connectivity.service.config.HonoConfig;
import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.eclipse.ditto.json.JsonFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * Unit test for {@link DefaultHonoConnectionFactory}.
 */
public final class DefaultHonoConnectionFactoryTest {

    private static final Config TEST_CONFIG = ConfigFactory.load("test");

    @Rule
    public final ActorSystemResource actorSystemResource = ActorSystemResource.newInstance(TEST_CONFIG);

    private HonoConfig honoConfig;

    private static Connection generateConnectionObjectFromJsonFile( String fileName) throws IOException {
        final var testClassLoader = DefaultHonoConnectionFactoryTest.class.getClassLoader();
        try (final var connectionJsonFileStreamReader = new InputStreamReader(
                testClassLoader.getResourceAsStream(fileName)
        )) {
            return ConnectivityModelFactory.connectionFromJson(
                    JsonFactory.readFrom(connectionJsonFileStreamReader).asObject());
        }
    }

    @Before
    public void before() {
        honoConfig = new DefaultHonoConfig(actorSystemResource.getActorSystem());
    }

    @Test
    public void newInstanceWithNullActorSystemThrowsException() {
        Assertions.assertThatNullPointerException()
                .isThrownBy(() -> new DefaultHonoConnectionFactory(null, ConfigFactory.empty()))
                .withMessage("The actorSystem must not be null!")
                .withNoCause();
    }

    @Test
    public void getHonoConnectionWithCustomMappingsReturnsExpected() throws IOException {
        final var userProvidedHonoConnection =
                generateConnectionObjectFromJsonFile("hono-connection-custom-test.json");
        final var expectedHonoConnection =
                generateConnectionObjectFromJsonFile("hono-connection-custom-expected.json");

        final var underTest =
                new DefaultHonoConnectionFactory(actorSystemResource.getActorSystem(), ConfigFactory.empty());

        assertThat(underTest.getHonoConnection(userProvidedHonoConnection)).isEqualTo(expectedHonoConnection);
    }

    @Test
    public void getHonoConnectionWithDefaultMappingReturnsExpected() throws IOException {
        final var userProvidedHonoConnection =
                generateConnectionObjectFromJsonFile("hono-connection-default-test.json");

        final var underTest =
                new DefaultHonoConnectionFactory(actorSystemResource.getActorSystem(), ConfigFactory.empty());

        assertThat(underTest.getHonoConnection(userProvidedHonoConnection))
                .isEqualTo(getExpectedHonoConnection(userProvidedHonoConnection));
    }

    @SuppressWarnings("unchecked")
    private Connection getExpectedHonoConnection(final Connection originalConnection) {
        final var sourcesByAddress = getSourcesByAddress(originalConnection.getSources());
        final var commandReplyTargetHeaderMapping = ConnectivityModelFactory.newHeaderMapping(Map.of(
                "correlation-id", "{{ header:correlation-id }}",
                "device_id", "{{ thing:id }}",
                "subject",
                "{{ header:subject | fn:default(topic:action-subject) | fn:default(topic:criterion) }}-response"
        ));
        final var targets = originalConnection.getTargets();
        final var basicAdditionalTargetHeaderMappingEntries = Map.of(
                "device_id", "{{ thing:id }}",
                "correlation-id", "{{ header:correlation-id }}",
                "subject", "{{ header:subject | fn:default(topic:action-subject) }}"
        );
        final var connectionId = originalConnection.getId();
        return ConnectivityModelFactory.newConnectionBuilder(originalConnection)
                .uri(honoConfig.getBaseUri().toString().replaceFirst("(\\S+://)(\\S+)",
                        "$1" + URLEncoder.encode(honoConfig.getUserPasswordCredentials().getUsername(), StandardCharsets.UTF_8)
                                + ":" + URLEncoder.encode(honoConfig.getUserPasswordCredentials().getPassword(), StandardCharsets.UTF_8)
                                + "@$2"))
                .validateCertificate(honoConfig.isValidateCertificates())
                .specificConfig(Map.of(
                        "saslMechanism", honoConfig.getSaslMechanism().toString(),
                        "bootstrapServers", TEST_CONFIG.getString(HonoConfig.PREFIX + ".bootstrap-servers"),
                        "groupId", originalConnection.getId().toString())
                )
                .setSources(List.of(
                        ConnectivityModelFactory.newSourceBuilder(sourcesByAddress.get(TELEMETRY.getAliasValue()))
                                .addresses(Set.of(getExpectedResolvedSourceAddress(TELEMETRY, connectionId)))
                                .replyTarget(ReplyTarget.newBuilder()
                                        .address(getExpectedResolvedCommandTargetAddress(connectionId))
                                        .headerMapping(commandReplyTargetHeaderMapping)
                                        .build())
                                .build(),
                        ConnectivityModelFactory.newSourceBuilder(sourcesByAddress.get(EVENT.getAliasValue()))
                                .addresses(Set.of(getExpectedResolvedSourceAddress(EVENT, connectionId)))
                                .replyTarget(ReplyTarget.newBuilder()
                                        .address(getExpectedResolvedCommandTargetAddress(connectionId))
                                        .headerMapping(commandReplyTargetHeaderMapping)
                                        .build())
                                .build(),
                        ConnectivityModelFactory.newSourceBuilder(
                                        sourcesByAddress.get(COMMAND_RESPONSE.getAliasValue()))
                                .addresses(Set.of(getExpectedResolvedSourceAddress(COMMAND_RESPONSE, connectionId)))
                                .headerMapping(ConnectivityModelFactory.newHeaderMapping(Map.of(
                                        "correlation-id", "{{ header:correlation-id }}",
                                        "status", "{{ header:status }}"
                                )))
                                .build()
                ))
                .setTargets(List.of(
                        ConnectivityModelFactory.newTargetBuilder(targets.get(0))
                                .address(getExpectedResolvedCommandTargetAddress(connectionId))
                                .originalAddress(getExpectedResolvedCommandTargetAddress(connectionId))
                                .headerMapping(ConnectivityModelFactory.newHeaderMapping(
                                        Stream.concat(
                                                basicAdditionalTargetHeaderMappingEntries.entrySet().stream(),
                                                Stream.of(Map.entry("response-required",
                                                        "{{ header:response-required }}"))
                                        ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                                ))
                                .build(),
                        ConnectivityModelFactory.newTargetBuilder(targets.get(1))
                                .address(getExpectedResolvedCommandTargetAddress(connectionId))
                                .originalAddress(getExpectedResolvedCommandTargetAddress(connectionId))
                                .headerMapping(ConnectivityModelFactory.newHeaderMapping(
                                        basicAdditionalTargetHeaderMappingEntries
                                ))
                                .build()
                ))
                .build();
    }

    private static Map<String, Source> getSourcesByAddress(final Iterable<Source> sources) {
        final var result = new LinkedHashMap<String, Source>();
        sources.forEach(source -> source.getAddresses().forEach(address -> result.put(address, source)));
        return result;
    }

    private static String getExpectedResolvedSourceAddress(final HonoAddressAlias honoAddressAlias, final ConnectionId connectionId) {
        return "hono." + honoAddressAlias.getAliasValue() + "." + connectionId;
    }

    private static String getExpectedResolvedCommandTargetAddress(final ConnectionId connectionId) {
        return "hono." + HonoAddressAlias.COMMAND.getAliasValue() + "." + connectionId + "/{{thing:id}}";
    }

}
