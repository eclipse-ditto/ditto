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
package org.eclipse.ditto.signals.commands.connectivity.exceptions;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.connectivity.modify.OpenConnection;
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
        final ConnectionSignalIllegalException exception = ConnectionSignalIllegalException.newBuilder("connection-id")
                .operationName("busy")
                .timeout(1)
                .build();

        final String jsonString = exception.toJsonString();

        final ConnectionSignalIllegalException failedExceptionFromJson =
                ConnectionSignalIllegalException.fromJson(JsonFactory.readFrom(jsonString).asObject(), DittoHeaders.empty());

        Assertions.assertThat(failedExceptionFromJson.toJsonString()).isEqualTo(exception.toJsonString());
    }

    @Test
    public void serializeStayingMessage() {
        final ConnectionSignalIllegalException exception = ConnectionSignalIllegalException.newBuilder("connection-id")
                .illegalSignalForState(OpenConnection.TYPE, "open")
                .build();

        final String jsonString = exception.toJsonString();

        final ConnectionSignalIllegalException failedExceptionFromJson =
                ConnectionSignalIllegalException.fromJson(JsonFactory.readFrom(jsonString).asObject(), DittoHeaders.empty());

        Assertions.assertThat(failedExceptionFromJson.toJsonString()).isEqualTo(exception.toJsonString());
    }

}
