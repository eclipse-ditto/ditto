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
import org.eclipse.ditto.connectivity.model.Source;
import org.junit.Test;

public final class KafkaConsumerGroupSpecificConfigTest {

    private final KafkaConsumerGroupSpecificConfig underTest = KafkaConsumerGroupSpecificConfig.getInstance();

    @Test
    public void isNotApplicableToConnectionWithoutSources() {
        final Connection connection = mock(Connection.class);
        when(connection.getSources()).thenReturn(List.of());
        assertThat(underTest.isApplicable(connection)).isFalse();
    }

    @Test
    public void isApplicableToConnectionWithSources() {
        final Connection connection = mock(Connection.class);
        final Source source = mock(Source.class);
        when(connection.getSources()).thenReturn(List.of(source));
        assertThat(underTest.isApplicable(connection)).isTrue();
    }

    @Test
    public void invalidCharactersCauseConnectionConfigurationInvalidException() {
        final Map<String, String> specificConfig = Map.of("groupId", "invalidCharacterÜ");
        final Connection connection = mock(Connection.class);
        when(connection.getSpecificConfig()).thenReturn(specificConfig);
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        assertThatCode(() -> underTest.validateOrThrow(connection, dittoHeaders))
                .isExactlyInstanceOf(ConnectionConfigurationInvalidException.class);
    }

    @Test
    public void validCharactersCauseNoException() {
        final Map<String, String> specificConfig = Map.of("groupId", "only-Valid-Characters-1234");
        final Connection connection = mock(Connection.class);
        when(connection.getSpecificConfig()).thenReturn(specificConfig);
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        assertThatCode(() -> underTest.validateOrThrow(connection, dittoHeaders))
                .doesNotThrowAnyException();
    }

    @Test
    public void invalidCharactersAreInvalid() {
        final Map<String, String> specificConfig = Map.of("groupId", "invalidCharacterÜ");
        final Connection connection = mock(Connection.class);
        when(connection.getSpecificConfig()).thenReturn(specificConfig);
        assertThat(underTest.isValid(connection)).isFalse();
    }

    @Test
    public void validCharactersAreValid() {
        final Map<String, String> specificConfig = Map.of("groupId", "only-Valid-Characters-1234");
        final Connection connection = mock(Connection.class);
        when(connection.getSpecificConfig()).thenReturn(specificConfig);
        assertThat(underTest.isValid(connection)).isTrue();
    }

    @Test
    public void emptyGroupIdIsInvalid() {
        final Map<String, String> specificConfig = Map.of("groupId", "");
        final Connection connection = mock(Connection.class);
        when(connection.getSpecificConfig()).thenReturn(specificConfig);
        assertThat(underTest.isValid(connection)).isFalse();
    }

    @Test
    public void applyReturnsGroupIdForConsumerConfigKey() {
        final Map<String, String> specificConfig = Map.of("groupId", "only-Valid-Characters-1234");
        final Connection connection = mock(Connection.class);
        when(connection.getSpecificConfig()).thenReturn(specificConfig);

        final Map<String, String> expectedConfig = Map.of(ConsumerConfig.GROUP_ID_CONFIG, "only-Valid-Characters-1234");
        assertThat(underTest.apply(connection)).isEqualTo(expectedConfig);
    }

}
