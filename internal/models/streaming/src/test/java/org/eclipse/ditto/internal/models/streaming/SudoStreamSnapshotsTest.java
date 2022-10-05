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
package org.eclipse.ditto.internal.models.streaming;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.List;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.Jsonifiable;
import org.eclipse.ditto.base.model.signals.commands.GlobalCommandRegistry;
import org.eclipse.ditto.json.JsonArray;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link SudoStreamSnapshots}.
 */
public final class SudoStreamSnapshotsTest {

    private static final EntityType THING_TYPE = EntityType.of("thing");

    @Test
    public void assertImmutability() {
        assertInstancesOf(SudoStreamSnapshots.class, areImmutable(),
                provided(EntityId.class, JsonArray.class).areAlsoImmutable(),
                assumingFields("namespaces").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SudoStreamSnapshots.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void testSerialization() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
        final SudoStreamSnapshots underTest =
                SudoStreamSnapshots.of(123, 456L, List.of("hello", "world"), dittoHeaders, THING_TYPE);
        final JsonObject serialized = underTest.toJson(FieldType.regularOrSpecial());
        final SudoStreamSnapshots deserialized = SudoStreamSnapshots.fromJson(serialized, dittoHeaders);

        assertThat(deserialized).isEqualTo(underTest);
    }

    @Test
    public void parseWithRegistry() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
        final SudoStreamSnapshots underTest =
                SudoStreamSnapshots.of(123, 456L, List.of("hello", "world"), dittoHeaders, THING_TYPE);
        final SudoStreamSnapshots expected = SudoStreamSnapshots.fromJson(underTest.toJson(), dittoHeaders);
        final Jsonifiable<?> parsed = GlobalCommandRegistry.getInstance().parse(underTest.toJson(), dittoHeaders);
        assertThat(parsed).isEqualTo(expected);
    }

}
