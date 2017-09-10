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
package org.eclipse.ditto.services.models.things;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.eclipse.ditto.services.models.things.TestConstants.Thing.REVISION_NUMBER;
import static org.eclipse.ditto.services.models.things.TestConstants.Thing.THING_ID;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ThingTag}.
 */
public final class ThingTagTest {

    private static final JsonValue KNOWN_JSON = JsonFactory.newObjectBuilder() //
            .set(ThingTag.JsonFields.ID, THING_ID) //
            .set(ThingTag.JsonFields.REVISION, REVISION_NUMBER) //
            .build();

    /** */
    @Test
    public void assertImmutability() {
        assertInstancesOf(ThingTag.class, areImmutable());
    }

    /** */
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ThingTag.class) //
                .usingGetClass() //
                .withRedefinedSuperclass() //
                .verify();
    }

    /** */
    @Test
    public void toJsonReturnsExpected() {
        final ThingTag underTest = ThingTag.of(THING_ID, REVISION_NUMBER);
        final JsonValue jsonValue = underTest.toJson();

        assertThat(jsonValue).isEqualTo(KNOWN_JSON);
    }

    /** */
    @Test
    public void createInstanceFromValidJson() {
        final ThingTag underTest = ThingTag.fromJson(KNOWN_JSON.toString());

        assertThat(underTest).isNotNull();
        assertThat(underTest.getThingId()).isEqualTo(THING_ID);
        assertThat(underTest.getRevision()).isEqualTo(REVISION_NUMBER);
    }
}
