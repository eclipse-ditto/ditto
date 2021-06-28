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

package org.eclipse.ditto.connectivity.service.mapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.connectivity.model.Connection;
import org.eclipse.ditto.connectivity.service.config.ConnectivityConfig;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link DittoConnectionContext}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class DittoConnectionContextTest {

    @Mock
    private Connection connection;
    @Mock
    private ConnectivityConfig config;

    @Test
    public void of() {
        final DittoConnectionContext underTest = DittoConnectionContext.of(connection, config);

        assertThat(underTest.getConnection()).isEqualTo(connection);
        assertThat(underTest.getConnectivityConfig()).isEqualTo(config);
    }

    @Test
    public void withConnection() {
        final Connection otherConnection = Mockito.mock(Connection.class);
        final DittoConnectionContext underTest = DittoConnectionContext.of(connection, config);

        assertThat(underTest.withConnection(otherConnection).getConnection()).isEqualTo(otherConnection);
    }

    @Test
    public void withConnectivityConfig() {
        final ConnectivityConfig otherConfig = Mockito.mock(ConnectivityConfig.class);
        final DittoConnectionContext underTest = DittoConnectionContext.of(connection, config);

        assertThat(underTest.withConnectivityConfig(otherConfig).getConnectivityConfig()).isEqualTo(otherConfig);

    }

    @Test
    public void throwsOnNullArguments() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> DittoConnectionContext.of(null, config));
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> DittoConnectionContext.of(connection, null));
    }

    @Test
    public void testEqualsAndHashcode() {
        EqualsVerifier.forClass(DittoConnectionContext.class).verify();
    }

    @Test
    public void testImmutability() {
        assertInstancesOf(DittoConnectionContext.class, areImmutable(),
                provided(Connection.class, ConnectivityConfig.class).areAlsoImmutable());
    }

}
