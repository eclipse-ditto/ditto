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
package org.eclipse.ditto.base.model.namespaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
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
    public void serialize() {
        final DittoHeaders headers = DittoHeaders.newBuilder().correlationId("x").build();
        final NamespaceBlockedException underTest =
                NamespaceBlockedException.newBuilder("ns").dittoHeaders(headers).build();
        final JsonObject serialized = underTest.toJson();
        final NamespaceBlockedException deserialized = NamespaceBlockedException.fromJson(serialized, headers);

        assertThat(deserialized).isEqualTo(underTest);
    }

}
