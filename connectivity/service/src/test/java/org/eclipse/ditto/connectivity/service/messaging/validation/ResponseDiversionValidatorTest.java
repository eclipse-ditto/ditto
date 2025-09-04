/*
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
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

package org.eclipse.ditto.connectivity.service.messaging.validation;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.ditto.base.model.auth.AuthorizationContext;
import org.eclipse.ditto.base.model.auth.AuthorizationModelFactory;
import org.eclipse.ditto.base.model.auth.DittoAuthorizationContextType;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionBuilder;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.ConnectionType;
import org.eclipse.ditto.connectivity.model.ConnectivityModelFactory;
import org.eclipse.ditto.connectivity.model.ConnectivityStatus;
import org.eclipse.ditto.connectivity.model.Source;
import org.eclipse.ditto.connectivity.service.messaging.BaseClientActor;
import org.junit.Before;
import org.junit.Test;

import com.hivemq.client.mqtt.datatypes.MqttQos;

/**
 * Unit tests for {@link ResponseDiversionValidator}.
 */
public class ResponseDiversionValidatorTest {

    private static final ConnectionId CONNECTION_ID = ConnectionId.of("test:connection");
    private static final ConnectionId TARGET_CONNECTION_ID = ConnectionId.of("target-connection");
    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.empty();
    private static final AuthorizationContext AUTHORIZATION_CONTEXT = AuthorizationModelFactory.newAuthContext(
            DittoAuthorizationContextType.PRE_AUTHENTICATED_CONNECTION,
            AuthorizationModelFactory.newAuthSubject("nginx:ditto"));
    private ConnectionBuilder connectionBuilder;

    @Before
    public void setUp() {
        connectionBuilder = ConnectivityModelFactory.newConnectionBuilder(
                CONNECTION_ID,
                ConnectionType.MQTT,
                ConnectivityStatus.OPEN,
                "tcp://mqtt-broker:1883"
        );
    }

    @Test
    public void validResponseDiversionConfiguration() {
        final Map<String, String> headerMapping = Map.of(
                DittoHeaderDefinition.DIVERT_RESPONSE_TO_CONNECTION.getKey(), TARGET_CONNECTION_ID.toString()
        );
        
        final Source source = createSourceWithHeaderMapping(headerMapping);
        final Connection connection = connectionBuilder
                .sources(Collections.singletonList(source))
                .build();

        assertThatNoException()
                .isThrownBy(() -> ResponseDiversionValidator.validate(connection, DITTO_HEADERS));
    }

    @Test
    public void validResponseDiversionWithTypes() {
        final Map<String, String> headerMapping = Map.of(
                DittoHeaderDefinition.DIVERT_RESPONSE_TO_CONNECTION.getKey(), TARGET_CONNECTION_ID.toString(),
                DittoHeaderDefinition.DIVERT_EXPECTED_RESPONSE_TYPES.getKey(), "response,error,nack"
        );
        
        final Source source = createSourceWithHeaderMapping(headerMapping);
        final Connection connection = connectionBuilder
                .sources(Collections.singletonList(source))
                .build();

        assertThatNoException()
                .isThrownBy(() -> ResponseDiversionValidator.validate(connection, DITTO_HEADERS));
    }

    @Test
    public void invalidEmptyTargetConnectionId() {
        final Map<String, String> headerMapping = Map.of(
                DittoHeaderDefinition.DIVERT_RESPONSE_TO_CONNECTION.getKey(), ""
        );
        
        final Source source = createSourceWithHeaderMapping(headerMapping);
        final Connection connection = connectionBuilder
                .sources(Collections.singletonList(source))
                .build();

        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> ResponseDiversionValidator.validate(connection, DITTO_HEADERS))
                .withMessageContaining("must not be empty");
    }

    @Test
    public void invalidSelfDiversion() {
        final Map<String, String> headerMapping = Map.of(
                DittoHeaderDefinition.DIVERT_RESPONSE_TO_CONNECTION.getKey(), CONNECTION_ID.toString()
        );
        
        final Source source = createSourceWithHeaderMapping(headerMapping);
        final Connection connection = connectionBuilder
                .sources(Collections.singletonList(source))
                .build();

        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> ResponseDiversionValidator.validate(connection, DITTO_HEADERS))
                .withMessageContaining("It is pointless to divert responses to the originating connection");
    }

    @Test
    public void invalidConnectionIdFormat() {
        final Map<String, String> headerMapping = Map.of(
                DittoHeaderDefinition.DIVERT_RESPONSE_TO_CONNECTION.getKey(), "invalid connection id!"
        );
        
        final Source source = createSourceWithHeaderMapping(headerMapping);
        final Connection connection = connectionBuilder
                .sources(Collections.singletonList(source))
                .build();

        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> ResponseDiversionValidator.validate(connection, DITTO_HEADERS))
                .withMessageContaining("Invalid target connection ID format");
    }

    @Test
    public void invalidResponseType() {
        final Map<String, String> headerMapping = Map.of(
                DittoHeaderDefinition.DIVERT_RESPONSE_TO_CONNECTION.getKey(), TARGET_CONNECTION_ID.toString(),
                DittoHeaderDefinition.DIVERT_EXPECTED_RESPONSE_TYPES.getKey(), "response,invalid-type,error"
        );
        
        final Source source = createSourceWithHeaderMapping(headerMapping);
        final Connection connection = connectionBuilder
                .sources(Collections.singletonList(source))
                .specificConfig(Map.of(BaseClientActor.IS_DIVERSION_SOURCE, "true"))
                .build();

        assertThatExceptionOfType(ConnectionConfigurationInvalidException.class)
                .isThrownBy(() -> ResponseDiversionValidator.validate(connection, DITTO_HEADERS))
                .withMessageContaining("Invalid response type")
                .withMessageContaining("invalid-type")
                .withMessageContaining("Valid types are: response, error, nack");
    }

    @Test
    public void emptyResponseTypesIsValid() {
        final Map<String, String> headerMapping = Map.of(
                DittoHeaderDefinition.DIVERT_RESPONSE_TO_CONNECTION.getKey(), TARGET_CONNECTION_ID.toString(),
                DittoHeaderDefinition.DIVERT_EXPECTED_RESPONSE_TYPES.getKey(), ""
        );
        
        final Source source = createSourceWithHeaderMapping(headerMapping);
        final Connection connection = connectionBuilder
                .sources(Collections.singletonList(source))
                .build();

        assertThatNoException()
                .isThrownBy(() -> ResponseDiversionValidator.validate(connection, DITTO_HEADERS));
    }

    @Test
    public void whitespaceOnlyResponseTypesIsValid() {
        final Map<String, String> headerMapping = Map.of(
                DittoHeaderDefinition.DIVERT_RESPONSE_TO_CONNECTION.getKey(), TARGET_CONNECTION_ID.toString(),
                DittoHeaderDefinition.DIVERT_EXPECTED_RESPONSE_TYPES.getKey(), "   "
        );
        
        final Source source = createSourceWithHeaderMapping(headerMapping);

        final Connection connection = connectionBuilder
                .sources(Collections.singletonList(source))
                .build();

        assertThatNoException()
                .isThrownBy(() -> ResponseDiversionValidator.validate(connection, DITTO_HEADERS));
    }

    @Test
    public void multipleSourcesWithDifferentDiversionTargets() {
        final Source source1 = createSourceWithHeaderMapping(Map.of(
                DittoHeaderDefinition.DIVERT_RESPONSE_TO_CONNECTION.getKey(), "target-1"
        ));
        
        final Source source2 = createSourceWithHeaderMapping(Map.of(
                DittoHeaderDefinition.DIVERT_RESPONSE_TO_CONNECTION.getKey(), "target-2"
        ));
        
        final Connection connection = connectionBuilder
                .sources(List.of(source1, source2))
                .build();

        assertThatNoException()
                .isThrownBy(() -> ResponseDiversionValidator.validate(connection, DITTO_HEADERS));
    }

    @Test
    public void sourceWithoutDiversionIsValid() {
        final Source source = createSourceWithHeaderMapping(Map.of());

        final Connection connection = connectionBuilder
                .sources(Collections.singletonList(source))
                .build();

        assertThatNoException()
                .isThrownBy(() -> ResponseDiversionValidator.validate(connection, DITTO_HEADERS));
    }

    @Test
    public void mixedSourcesWithAndWithoutDiversion() {
        final Source sourceWithDiversion = createSourceWithHeaderMapping(Map.of(
                DittoHeaderDefinition.DIVERT_RESPONSE_TO_CONNECTION.getKey(), TARGET_CONNECTION_ID.toString()
        ));
        
        final Source sourceWithoutDiversion = createSourceWithHeaderMapping(Map.of());
        
        final Connection connection = connectionBuilder
                .sources(List.of(sourceWithDiversion, sourceWithoutDiversion))
                .build();

        assertThatNoException()
                .isThrownBy(() -> ResponseDiversionValidator.validate(connection, DITTO_HEADERS));
    }

    private Source createSourceWithHeaderMapping(final Map<String, String> mapping) {
        return ConnectivityModelFactory.newSourceBuilder()
                .authorizationContext(AUTHORIZATION_CONTEXT)
                .address("test/address")
                .qos(MqttQos.EXACTLY_ONCE.getCode())
                .headerMapping(ConnectivityModelFactory.newHeaderMapping(mapping))
                .build();
    }
}