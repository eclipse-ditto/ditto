/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.connectivity.model.signals.commands.exceptions;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.time.Duration;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.signals.commands.TestConstants;
import org.eclipse.ditto.connectivity.model.signals.commands.modify.OpenConnection;
import org.eclipse.ditto.json.JsonFactory;
import org.junit.Test;

/**
 * Tests {@link ConnectionSignalIllegalException}.
 */
public final class ConnectionSignalIllegalExceptionTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(ConnectionSignalIllegalException.class, areImmutable());
    }

    @Test
    public void serializeConnectingMessage() {
        final ConnectionSignalIllegalException exception = ConnectionSignalIllegalException.newBuilder(TestConstants.ID)
                .operationName("busy")
                .timeout(1)
                .timeout(Duration.ofSeconds(1))
                .build();

        final String jsonString = exception.toJsonString();

        final ConnectionSignalIllegalException failedExceptionFromJson =
                ConnectionSignalIllegalException.fromJson(JsonFactory.readFrom(jsonString).asObject(), DittoHeaders.empty());

        Assertions.assertThat(failedExceptionFromJson.toJsonString()).isEqualTo(exception.toJsonString());
    }

    @Test
    public void serializeStayingMessage() {
        final ConnectionSignalIllegalException exception = ConnectionSignalIllegalException.newBuilder(TestConstants.ID)
                .illegalSignalForState(OpenConnection.TYPE, "open")
                .build();

        final String jsonString = exception.toJsonString();

        final ConnectionSignalIllegalException failedExceptionFromJson = ConnectionSignalIllegalException.fromJson(
                JsonFactory.readFrom(jsonString).asObject(), DittoHeaders.empty()
        );

        Assertions.assertThat(failedExceptionFromJson.toJsonString()).isEqualTo(exception.toJsonString());
    }

}
