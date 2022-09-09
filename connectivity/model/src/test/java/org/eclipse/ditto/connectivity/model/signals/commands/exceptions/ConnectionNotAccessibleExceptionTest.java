/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.connectivity.model.signals.commands.TestConstants;
import org.eclipse.ditto.json.JsonFactory;
import org.junit.Test;

/**
 * Unit test for {@link ConnectionNotAccessibleException}.
 */
public final class ConnectionNotAccessibleExceptionTest {

    @Test
    public void checkToJsonFromJson() {
        final ConnectionNotAccessibleException exception = ConnectionNotAccessibleException.newBuilder(TestConstants.ID)
                .message("message")
                .description("description")
                .build();

        final String jsonString = exception.toJsonString();

        final ConnectionNotAccessibleException decoded =
                ConnectionNotAccessibleException.fromJson(JsonFactory.readFrom(jsonString).asObject(), DittoHeaders.empty());

        assertThat(decoded.toJsonString()).isEqualTo(exception.toJsonString());
    }

    @Test
    public void testImmutability() {
        assertInstancesOf(
                ConnectionNotAccessibleException.class,
                areImmutable());
    }

}
