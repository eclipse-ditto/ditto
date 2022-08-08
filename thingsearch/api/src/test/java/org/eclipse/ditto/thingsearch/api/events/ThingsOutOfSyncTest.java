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
package org.eclipse.ditto.thingsearch.api.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.ThingId;
import org.junit.Test;
import org.mutabilitydetector.unittesting.AllowedReason;
import org.mutabilitydetector.unittesting.MutabilityAssert;
import org.mutabilitydetector.unittesting.MutabilityMatchers;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link ThingsOutOfSync}.
 */
public final class ThingsOutOfSyncTest {

    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(ThingsOutOfSync.class, MutabilityMatchers.areImmutable(),
                AllowedReason.provided(DittoHeaders.class).isAlsoImmutable(),
                AllowedReason.assumingFields("thingIds")
                        .areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ThingsOutOfSync.class)
                .usingGetClass()
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void testSerialization() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
        final ThingsOutOfSync underTest = ThingsOutOfSync.of(
                Collections.singletonList(ThingId.of("namespace", "name")),
                dittoHeaders
        );
        final String jsonString = underTest.toJsonString();
        final ThingsOutOfSync deserializedCommand = ThingsOutOfSync.fromJson(JsonObject.of(jsonString), dittoHeaders);
        assertThat(deserializedCommand).isEqualTo(underTest);
    }

}
