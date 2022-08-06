/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.api.commands.sudo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonArray;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link StreamThings}.
 */
public final class StreamThingsTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(StreamThings.class, areImmutable(), provided(JsonArray.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(StreamThings.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void serializeAllOptionalFields() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
        final StreamThings underTest = StreamThings.of("eq(thingId,\"thing:id\")",
                JsonArray.of("thing", "namespace"),
                "sort(+thingId,-attributes/counter,+feature/acceleration/properties/z)",
                JsonArray.of("thing:hd", 53, 0.975),
                dittoHeaders
        );

        final StreamThings deserialized = StreamThings.fromJson(underTest.toJson(), dittoHeaders);
        assertThat(deserialized).isEqualTo(underTest);
    }

    @Test
    public void serializeNoOptionalField() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
        final StreamThings underTest = StreamThings.of(null, null, null, null, dittoHeaders);
        final StreamThings deserialized = StreamThings.fromJson(underTest.toJson(), dittoHeaders);
        assertThat(deserialized).isEqualTo(underTest);
    }
}
