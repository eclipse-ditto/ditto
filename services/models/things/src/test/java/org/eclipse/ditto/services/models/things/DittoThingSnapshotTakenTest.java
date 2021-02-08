/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.models.things;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.events.base.Event;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link DittoThingSnapshotTaken}.
 */
public final class DittoThingSnapshotTakenTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(Event.JsonFields.TYPE, DittoThingSnapshotTaken.TYPE)
            .set(DittoThingSnapshotTaken.JSON_THING_ID, TestConstants.Thing.THING_ID.toString())
            .build();


    @Test
    public void assertImmutability() {
        assertInstancesOf(DittoThingSnapshotTaken.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(DittoThingSnapshotTaken.class).verify();
    }

    @Test
    public void toJsonReturnsExpected() {
        final DittoThingSnapshotTaken underTest =
                DittoThingSnapshotTaken.of(TestConstants.Thing.THING_ID);
        final JsonObject actualJson = underTest.toJson();
        assertThat(actualJson).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void fromJsonWorksAsExpected() {
        final DittoThingSnapshotTaken underTest =
                DittoThingSnapshotTaken.fromJson(KNOWN_JSON, DittoHeaders.empty());

        Assertions.assertThat(underTest.getType()).isEqualTo(DittoThingSnapshotTaken.TYPE);
        Assertions.assertThat((CharSequence) underTest.getEntityId()).isEqualTo(TestConstants.Thing.THING_ID);
    }


}