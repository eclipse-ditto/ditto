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
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class KafkaConsumerOffsetResetSpecificConfigTest {

    private final KafkaConsumerOffsetResetSpecificConfig underTest =
            KafkaConsumerOffsetResetSpecificConfig.getInstance();
    @Mock
    private Connection connection;

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
    public void invalidOffsetResetCauseConnectionConfigurationInvalidException() {
        final Map<String, String> specificConfig = Map.of("consumerOffsetReset", "invalid");
        when(connection.getSpecificConfig()).thenReturn(specificConfig);
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        assertThatCode(() -> underTest.validateOrThrow(connection, dittoHeaders))
                .isExactlyInstanceOf(ConnectionConfigurationInvalidException.class);
    }

    @Test
    public void validOffsetResetCauseNoException() {
        final Map<String, String> specificConfig = Map.of("consumerOffsetReset", "earliest");
        when(connection.getSpecificConfig()).thenReturn(specificConfig);
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        assertThatCode(() -> underTest.validateOrThrow(connection, dittoHeaders))
                .doesNotThrowAnyException();
    }

    @Test
    public void invalidOffsetResetIsInvalid() {
        final Map<String, String> specificConfig = Map.of("consumerOffsetReset", "invalid");
        when(connection.getSpecificConfig()).thenReturn(specificConfig);
        assertThat(underTest.isValid(connection)).isFalse();
    }

    @Test
    public void validOffsetResetIsValid() {
        final Map<String, String> specificConfig = Map.of("consumerOffsetReset", "earliest");
        when(connection.getSpecificConfig()).thenReturn(specificConfig);
        assertThat(underTest.isValid(connection)).isTrue();
    }

    @Test
    public void emptyOffsetResetIsInvalid() {
        final Map<String, String> specificConfig = Map.of("consumerOffsetReset", "");
        when(connection.getSpecificConfig()).thenReturn(specificConfig);
        assertThat(underTest.isValid(connection)).isFalse();
    }

    @Test
    public void applyReturnsOffsetResetForConsumerConfigKey() {
        final Map<String, String> specificConfig = Map.of("consumerOffsetReset", "earliest");
        when(connection.getSpecificConfig()).thenReturn(specificConfig);

        final Map<String, String> expectedConfig =
                Map.of(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        assertThat(underTest.apply(connection)).isEqualTo(expectedConfig);
    }

}
