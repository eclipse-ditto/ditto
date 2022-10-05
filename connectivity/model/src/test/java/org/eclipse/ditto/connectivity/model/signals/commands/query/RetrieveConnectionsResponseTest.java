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

package org.eclipse.ditto.connectivity.model.signals.commands.query;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationSubject;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.signals.Signal;
import org.eclipse.ditto.base.model.signals.commands.GlobalCommandResponseRegistry;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.Topic;
import org.eclipse.ditto.connectivity.model.signals.commands.ConnectivityCommandResponse;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link RetrieveConnectionsResponse}.
 */
public final class RetrieveConnectionsResponseTest {

    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.empty();
    private static final Connection CONNECTION = createConnection(ConnectionId.of("uuid-1"));

    private static final JsonObject KNOWN_JSON = JsonObject.newBuilder()
            .set(ConnectivityCommandResponse.JsonFields.TYPE, RetrieveConnectionsResponse.TYPE)
            .set(ConnectivityCommandResponse.JsonFields.STATUS, HttpStatus.OK.getCode())
            .set(RetrieveConnectionsResponse.JSON_CONNECTIONS, JsonArray.of(CONNECTION.toJson()))
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveConnectionsResponse.class,
                areImmutable(),
                provided(JsonArray.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RetrieveConnectionsResponse.class)
                .withRedefinedSuperclass()
                .usingGetClass()
                .verify();
    }

    @Test
    public void tryToCreateInstanceWithNullThings() {
        assertThatNullPointerException()
                .isThrownBy(() -> RetrieveConnectionsResponse.of(null, FieldType.notHidden(), DITTO_HEADERS))
                .withMessage("The Connections must not be null!");
    }

    @Test
    public void tryToCreateInstanceWithNullJsonArray() {
        assertThatNullPointerException()
                .isThrownBy(() -> RetrieveConnectionsResponse.of((JsonArray) null, DITTO_HEADERS))
                .withMessage("The Connections must not be null!");
    }

    @Test
    public void toJsonReturnsExpected() {
        final RetrieveConnectionsResponse underTest = RetrieveConnectionsResponse.of(Collections.singletonList(CONNECTION),
                FieldType.notHidden(),
                DITTO_HEADERS);
        final JsonObject actualJson = underTest.toJson();

        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void getResourcePathReturnsExpected() {
        final JsonPointer expectedResourcePath = JsonPointer.of("/connections");

        final RetrieveConnectionsResponse underTest = RetrieveConnectionsResponse.of(Collections.singletonList(CONNECTION),
                FieldType.notHidden(),
                DITTO_HEADERS);

        assertThat(underTest.getResourcePath()).isEqualTo(expectedResourcePath);
    }

    @Test
    public void createInstanceFromValidJson() {
        final RetrieveConnectionsResponse underTest = RetrieveConnectionsResponse.fromJson(KNOWN_JSON.toString(), DITTO_HEADERS);

        assertThat(underTest).isNotNull();

        List<Connection> connections = underTest.getConnections();

        assertThat(connections).hasSize(1);
        assertThat(connections.get(0).toJson()).isEqualTo(CONNECTION.toJson());
    }

    @Test
    public void deserializeRetrieveConnectionsResponse() {
        final GlobalCommandResponseRegistry globalCommandResponseRegistry = GlobalCommandResponseRegistry.getInstance();
        final Signal<?> actual = globalCommandResponseRegistry.parse(RetrieveConnectionsResponseTest.KNOWN_JSON,
                RetrieveConnectionsResponseTest.DITTO_HEADERS);
        final Signal<?> expected =
                RetrieveConnectionsResponse.of(Collections.singletonList(RetrieveConnectionsResponseTest.CONNECTION),
                        FieldType.notHidden(),
                        RetrieveConnectionsResponseTest.DITTO_HEADERS);

        assertThat(actual).isEqualTo(expected);
    }

    private static Connection createConnection(final ConnectionId id) {
        final String subjectId = "integration:mySolution:test";
        final AuthorizationContext authorizationContext = AuthorizationContext.newInstance(DittoAuthorizationContextType.UNSPECIFIED,
                AuthorizationSubject.newInstance(subjectId));
        return ConnectivityModelFactory.newConnectionBuilder(id,
                        ConnectionType.AMQP_091,
                        ConnectivityStatus.OPEN,
                        "amqp://user:password@host:1234")
                .name("myConnection")
                .targets(Collections.singletonList(ConnectivityModelFactory.newTargetBuilder()
                        .address("test")
                        .authorizationContext(authorizationContext)
                        .topics(Topic.TWIN_EVENTS)
                        .build()))
                .build();
    }

}
