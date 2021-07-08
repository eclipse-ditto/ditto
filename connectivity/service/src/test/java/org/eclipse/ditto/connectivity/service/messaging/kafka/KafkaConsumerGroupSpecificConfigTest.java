/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.model.ConnectionConfigurationInvalidException;
import org.eclipse.ditto.connectivity.model.ConnectionId;
import org.eclipse.ditto.connectivity.model.Source;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class KafkaConsumerGroupSpecificConfigTest {

    private static final ConnectionId CONNECTION_ID = ConnectionId.generateRandom();
    private final KafkaConsumerGroupSpecificConfig underTest = KafkaConsumerGroupSpecificConfig.getInstance();
    @Mock
    private Connection connection;

    @Before
    public void setup() {
        when(connection.getId()).thenReturn(CONNECTION_ID);
    }

    @Test
    public void isNotApplicableToConnectionWithoutSources() {
        when(connection.getSources()).thenReturn(List.of());
        assertThat(underTest.isApplicable(connection)).isFalse();
    }

    @Test
    public void isApplicableToConnectionWithSources() {
        final Source source = mock(Source.class);
        when(connection.getSources()).thenReturn(List.of(source));
        assertThat(underTest.isApplicable(connection)).isTrue();
    }

    @Test
    public void invalidCharactersCauseConnectionConfigurationInvalidException() {
        final Map<String, String> specificConfig = Map.of("groupId", "invalidCharacterÜ");
        when(connection.getSpecificConfig()).thenReturn(specificConfig);
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        assertThatCode(() -> underTest.validateOrThrow(connection, dittoHeaders))
                .isExactlyInstanceOf(ConnectionConfigurationInvalidException.class);
    }

    @Test
    public void validCharactersCauseNoException() {
        final Map<String, String> specificConfig = Map.of("groupId", "only-Valid-Characters-1234");
        when(connection.getSpecificConfig()).thenReturn(specificConfig);
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        assertThatCode(() -> underTest.validateOrThrow(connection, dittoHeaders))
                .doesNotThrowAnyException();
    }

    @Test
    public void invalidCharactersAreInvalid() {
        final Map<String, String> specificConfig = Map.of("groupId", "invalidCharacterÜ");
        when(connection.getSpecificConfig()).thenReturn(specificConfig);
        assertThat(underTest.isValid(connection)).isFalse();
    }

    @Test
    public void validCharactersAreValid() {
        final Map<String, String> specificConfig = Map.of("groupId", "only-Valid-Characters-1234");
        when(connection.getSpecificConfig()).thenReturn(specificConfig);
        assertThat(underTest.isValid(connection)).isTrue();
    }

    @Test
    public void emptyGroupIdIsInvalid() {
        final Map<String, String> specificConfig = Map.of("groupId", "");
        when(connection.getSpecificConfig()).thenReturn(specificConfig);
        assertThat(underTest.isValid(connection)).isFalse();
    }

    @Test
    public void applyReturnsGroupIdForConsumerConfigKey() {
        final Map<String, String> specificConfig = Map.of("groupId", "only-Valid-Characters-1234");
        when(connection.getSpecificConfig()).thenReturn(specificConfig);

        final Map<String, String> expectedConfig = Map.of(ConsumerConfig.GROUP_ID_CONFIG, "only-Valid-Characters-1234");
        assertThat(underTest.apply(connection)).isEqualTo(expectedConfig);
    }

    @Test
    public void resolvesConnectionIdPlaceholder() {
        final Map<String, String> specificConfig = Map.of("groupId", "test_{{connection:id}}");
        when(connection.getSpecificConfig()).thenReturn(specificConfig);

        final Map<String, String> expectedConfig = Map.of(ConsumerConfig.GROUP_ID_CONFIG, "test_" + CONNECTION_ID);
        assertThat(underTest.apply(connection)).isEqualTo(expectedConfig);
    }

}
