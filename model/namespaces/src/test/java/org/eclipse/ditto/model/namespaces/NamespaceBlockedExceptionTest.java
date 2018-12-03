/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.namespaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.junit.Test;

/**
 * Unit test for {@link NamespaceBlockedException}.
 */
public final class NamespaceBlockedExceptionTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(NamespaceBlockedException.class, areImmutable());
    }

    @Test
    public void copy() {
        final NamespaceBlockedException underTest = NamespaceBlockedException.newBuilder("ns").build();
        final DittoRuntimeException copy = DittoRuntimeException.newBuilder(underTest).build();

        assertThat(copy.getMessage()).isEqualTo(underTest.getMessage());
        assertThat(copy.getDescription()).isEqualTo(underTest.getDescription());
        assertThat(copy.getHref()).isEqualTo(underTest.getHref());
        assertThat(copy.getDittoHeaders()).isEqualTo(underTest.getDittoHeaders());
    }

    @Test
    public void serialize() {
        final DittoHeaders headers = DittoHeaders.newBuilder().correlationId("x").build();
        final NamespaceBlockedException underTest =
                NamespaceBlockedException.newBuilder("ns").dittoHeaders(headers).build();
        final JsonObject serialized = underTest.toJson();
        final NamespaceBlockedException deserialized = NamespaceBlockedException.fromJson(serialized, headers);

        assertThat(deserialized).isEqualTo(underTest);
    }

}
