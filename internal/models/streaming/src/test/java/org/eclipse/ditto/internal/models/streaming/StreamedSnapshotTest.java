/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.models.streaming;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link StreamedSnapshot}.
 */
public final class StreamedSnapshotTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(StreamedSnapshot.class, areImmutable(),
                provided(EntityId.class, JsonObject.class).areAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(StreamedSnapshot.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void testSerialization() {
        final StreamedSnapshot underTest =
                StreamedSnapshot.of(EntityId.of(EntityType.of("thing"), "hello:world"),
                        JsonObject.of("{\"hello\":\"world\"}"));

        final StreamedSnapshot deserialized = StreamedSnapshot.fromJson(underTest.toJson());

        assertThat(deserialized).isEqualTo(underTest);
    }
}
