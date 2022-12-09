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

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.connectivity.service.messaging.TestConstants.Authorization.AUTHORIZATION_CONTEXT;

import java.util.List;
import java.util.stream.Stream;

import org.assertj.core.api.JUnitSoftAssertions;
import org.eclipse.ditto.base.model.correlationid.TestNameCorrelationId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.Enforcement;
import org.eclipse.ditto.connectivity.model.HonoAddressAlias;
import org.eclipse.ditto.connectivity.model.Topic;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.eclipse.ditto.connectivity.service.messaging.TestConstants;
import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.eclipse.ditto.placeholders.UnresolvedPlaceholderException;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Unit test for {@link org.eclipse.ditto.connectivity.service.messaging.hono.HonoValidator}.
 */
public final class HonoValidatorTest {

    @ClassRule
    public static final ActorSystemResource ACTOR_SYSTEM_RESOURCE =
            ActorSystemResource.newInstance(TestConstants.CONFIG);

    private static final ConnectionId CONNECTION_ID = TestConstants.createRandomConnectionId();
    private static final ConnectivityConfig CONNECTIVITY_CONFIG = TestConstants.CONNECTIVITY_CONFIG;

    @Rule
    public final TestNameCorrelationId testNameCorrelationId = TestNameCorrelationId.newInstance();

    @Rule
    public final JUnitSoftAssertions softly = new JUnitSoftAssertions();

    private HonoValidator underTest;

    @Before
    public void before() {
        underTest = HonoValidator.getInstance();
    }

    @Test
    public void validateWithValidEnforcementThrowsNoException() {
        assertThatCode(
                () -> underTest.validate(getConnectionWithSourceEnforcement(
                                ConnectivityModelFactory.newEnforcement("{{ header:device_id }}",
                                        "{{ thing:id }}",
                                        "{{ thing:name }}",
                                        "{{ thing:namespace }}")
                        ),
                        getDittoHeadersWithCorrelationId(),
                        ACTOR_SYSTEM_RESOURCE.getActorSystem(),
                        CONNECTIVITY_CONFIG)
        ).doesNotThrowAnyException();
    }

    @Test
    public void validateWithInvalidMatcherThrowsException() {
        final var dittoHeaders = getDittoHeadersWithCorrelationId();

        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> underTest.validate(getConnectionWithSourceEnforcement(
                                ConnectivityModelFactory.newEnforcement(
                                        "{{ header:device_id }}",
                                        "{{ header:ditto }}")
                        ),
                        dittoHeaders,
                        ACTOR_SYSTEM_RESOURCE.getActorSystem(),
                        CONNECTIVITY_CONFIG))
                .withCauseInstanceOf(UnresolvedPlaceholderException.class)
                .satisfies(exception -> assertThat(exception.getDittoHeaders()).isEqualTo(dittoHeaders));
    }

    @Test
    public void validateWithValidSourceAddressesThrowsNoException() {
        final var dittoHeaders = getDittoHeadersWithCorrelationId();
        Stream.of(HonoAddressAlias.values())
                .filter(honoAddressAlias -> HonoAddressAlias.COMMAND != honoAddressAlias)
                .map(HonoAddressAlias::getAliasValue)
                .forEach(honoAddressAliasValue -> softly.assertThatCode(() -> underTest.validate(
                                getConnectionWithSourceAddress(honoAddressAliasValue),
                                dittoHeaders,
                                ACTOR_SYSTEM_RESOURCE.getActorSystem(),
                                CONNECTIVITY_CONFIG
                        ))
                        .as(honoAddressAliasValue)
                        .doesNotThrowAnyException());
    }

    @Test
    public void validateWithEmptySourceAddressThrowsException() {
        final var dittoHeaders = getDittoHeadersWithCorrelationId();

        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> underTest.validate(getConnectionWithSourceAddress(""),
                        dittoHeaders,
                        ACTOR_SYSTEM_RESOURCE.getActorSystem(),
                        CONNECTIVITY_CONFIG))
                .withMessage("The provided source address must not be empty.")
                .withNoCause()
                .satisfies(exception -> assertThat(exception.getDittoHeaders()).isEqualTo(dittoHeaders));
    }

    @Test
    public void validateWithInvalidSourceAddressesThrowsException() {
        final var dittoHeaders = getDittoHeadersWithCorrelationId();

        Stream.of(
                HonoAddressAlias.COMMAND.getAliasValue(),
                "events/",
                "hono.telemetry.c4bc9a62-8516-4232-bb81-dbbfe4d0fa8c_hub",
                "ditto*a"
        ).forEach(
                invalidSourceAddress -> softly.assertThatThrownBy(
                                () -> underTest.validate(getConnectionWithSourceAddress(invalidSourceAddress),
                                        dittoHeaders,
                                        ACTOR_SYSTEM_RESOURCE.getActorSystem(),
                                        CONNECTIVITY_CONFIG)
                        )
                        .as(invalidSourceAddress)
                        .hasMessageStartingWith("The provided source address <%s> is invalid." +
                                        " It should be one of the defined aliases: ",
                                invalidSourceAddress)
                        .hasMessageContainingAll(Stream.of(HonoAddressAlias.values())
                                .filter(honoAddressAlias -> HonoAddressAlias.COMMAND !=honoAddressAlias)
                                .map(HonoAddressAlias::getAliasValue)
                                .toArray(CharSequence[]::new))
                        .hasNoCause()
                        .isInstanceOfSatisfying(ConnectionConfigurationInvalidException.class,
                                exception -> assertThat(exception.getDittoHeaders()).isEqualTo(dittoHeaders))
        );
    }

    @Test
    public void validateWithInvalidSourceQosThrowsException() {
        final var invalidQos = 3;
        final var dittoHeaders = getDittoHeadersWithCorrelationId();

        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> underTest.validate(ConnectivityModelFactory.newConnectionBuilder(CONNECTION_ID,
                                        ConnectionType.HONO,
                                        ConnectivityStatus.OPEN,
                                        "tcp://localhost:999999")
                                .sources(singletonList(ConnectivityModelFactory.newSourceBuilder()
                                        .address("event")
                                        .authorizationContext(AUTHORIZATION_CONTEXT)
                                        .qos(invalidQos)
                                        .build()))
                                .build(),
                        dittoHeaders,
                        ACTOR_SYSTEM_RESOURCE.getActorSystem(),
                        CONNECTIVITY_CONFIG))
                .withMessage("Invalid source 'qos' value <%d>. Supported values are <0> and <1>.", invalidQos)
                .withNoCause()
                .satisfies(exception -> assertThat(exception.getDittoHeaders()).isEqualTo(dittoHeaders));
    }

    @Test
    public void validateWithValidTargetAddressThrowsNoException() {
        assertThatCode(
                () -> underTest.validate(getConnectionWithTargetAddress(HonoAddressAlias.COMMAND.getAliasValue()),
                        getDittoHeadersWithCorrelationId(),
                        ACTOR_SYSTEM_RESOURCE.getActorSystem(),
                        CONNECTIVITY_CONFIG)
        ).doesNotThrowAnyException();
    }

    @Test
    public void validateWithEmptyTargetAddressThrowsException() {
        final var dittoHeaders = getDittoHeadersWithCorrelationId();

        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> underTest.validate(getConnectionWithTargetAddress(""),
                        dittoHeaders,
                        ACTOR_SYSTEM_RESOURCE.getActorSystem(),
                        CONNECTIVITY_CONFIG))
                .withMessage("The provided target address must not be empty.")
                .withNoCause()
                .satisfies(exception -> assertThat(exception.getDittoHeaders()).isEqualTo(dittoHeaders));
    }

    @Test
    public void validateWithInvalidTargetAddressesThrowsException() {
        final var dittoHeaders = getDittoHeadersWithCorrelationId();

        Stream.concat(
                Stream.of(HonoAddressAlias.values())
                        .filter(honoAddressAlias -> HonoAddressAlias.COMMAND != honoAddressAlias)
                        .map(HonoAddressAlias::getAliasValue),
                Stream.of("hono.command.c4bc9a62-8516-4232-bb81-dbbfe4d0fa8c_hub/{{thing:id}}")
        ).forEach(
                invalidTargetAddress -> softly.assertThatThrownBy(
                                () -> underTest.validate(getConnectionWithTargetAddress(invalidTargetAddress),
                                        dittoHeaders,
                                        ACTOR_SYSTEM_RESOURCE.getActorSystem(),
                                        CONNECTIVITY_CONFIG)
                        )
                        .as(invalidTargetAddress)
                        .hasMessage("The provided target address <%s> is invalid. It should be <%s>.",
                                invalidTargetAddress,
                                HonoAddressAlias.COMMAND.getAliasValue())
                        .hasNoCause()
                        .isInstanceOfSatisfying(ConnectionConfigurationInvalidException.class,
                                exception -> assertThat(exception.getDittoHeaders()).isEqualTo(dittoHeaders))
        );
    }

    private static Connection getConnectionWithSourceEnforcement(final Enforcement sourceEnforcement) {
        return ConnectivityModelFactory.newConnectionBuilder(CONNECTION_ID,
                        ConnectionType.HONO,
                        ConnectivityStatus.OPEN,
                        "tcp://localhost:99999")
                .sources(List.of(ConnectivityModelFactory.newSourceBuilder()
                        .address(HonoAddressAlias.TELEMETRY.getAliasValue())
                        .authorizationContext(AUTHORIZATION_CONTEXT)
                        .enforcement(sourceEnforcement)
                        .qos(1)
                        .build()))
                .build();
    }

    private static Connection getConnectionWithSourceAddress(final String sourceAddress) {
        return ConnectivityModelFactory.newConnectionBuilder(CONNECTION_ID,
                        ConnectionType.HONO,
                        ConnectivityStatus.OPEN,
                        "tcp://localhost:99999")
                .sources(List.of(ConnectivityModelFactory.newSourceBuilder()
                        .address(sourceAddress)
                        .authorizationContext(AUTHORIZATION_CONTEXT)
                        .qos(1)
                        .build()))
                .build();
    }

    private static Connection getConnectionWithTargetAddress(final String targetAddress) {
        return ConnectivityModelFactory.newConnectionBuilder(CONNECTION_ID,
                        ConnectionType.HONO,
                        ConnectivityStatus.OPEN,
                        "tcp://localhost:1883")
                .targets(singletonList(ConnectivityModelFactory.newTargetBuilder()
                        .address(targetAddress)
                        .authorizationContext(AUTHORIZATION_CONTEXT)
                        .qos(1)
                        .topics(Topic.LIVE_EVENTS)
                        .build()))
                .build();
    }

    private DittoHeaders getDittoHeadersWithCorrelationId() {
        return DittoHeaders.newBuilder().correlationId(testNameCorrelationId.getCorrelationId()).build();
    }

}

