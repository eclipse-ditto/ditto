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

import static org.eclipse.ditto.base.model.assertions.DittoBaseAssertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.signals.commands.TestConstants;
import org.eclipse.ditto.json.JsonFactory;
import org.junit.Test;

/**
 * Tests {@link ConnectionFailedException}.
 */
public final class ConnectionFailedExceptionTest {


    @Test
    public void assertImmutability() {
        assertInstancesOf(ConnectionFailedException.class, areImmutable());
    }

    @Test
    public void checkToJsonFromJson() {
        final ConnectionFailedException failedException = ConnectionFailedException.newBuilder(TestConstants.ID)
                .message("message")
                .description("description")
                .build();

        final String jsonString = failedException.toJsonString();

        final ConnectionFailedException failedExceptionFromJson =
                ConnectionFailedException.fromJson(JsonFactory.readFrom(jsonString).asObject(), DittoHeaders.empty());

        assertThat(failedExceptionFromJson.toJsonString()).isEqualTo(failedException.toJsonString());
    }

}
