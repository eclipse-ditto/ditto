/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */

package org.eclipse.ditto.signals.commands.connectivity.exceptions;

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.junit.Test;

public final class ConnectionBusyExceptionTest {


    @Test
    public void assertImmutability() {
        assertInstancesOf(ConnectionBusyException.class, areImmutable());
    }

    @Test
    public void checkToJsonFromJson() {
        final ConnectionBusyException exception = ConnectionBusyException.newBuilder("connection-id")
                .operationName("busy")
                .timeout(1)
                .build();

        final String jsonString = exception.toJsonString();

        final ConnectionBusyException failedExceptionFromJson =
                ConnectionBusyException.fromJson(JsonFactory.readFrom(jsonString).asObject(), DittoHeaders.empty());

        Assertions.assertThat(failedExceptionFromJson.toJsonString()).isEqualTo(exception.toJsonString());
    }

}
