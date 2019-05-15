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
package org.eclipse.ditto.services.models.things;

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ThingTag}.
 */
public final class ThingTagTest {

    private static final JsonObject KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(ThingTag.JsonFields.ID, TestConstants.Thing.THING_ID)
            .set(ThingTag.JsonFields.REVISION, TestConstants.Thing.REVISION_NUMBER)
            .build();

    @Test
    public void assertImmutability() {
        assertInstancesOf(ThingTag.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ThingTag.class)
                .withRedefinedSuperclass()
                .verify();
    }

    @Test
    public void toJsonReturnsExpected() {
        final ThingTag underTest = ThingTag.of(TestConstants.Thing.THING_ID, TestConstants.Thing.REVISION_NUMBER);
        final JsonValue jsonValue = underTest.toJson();

        assertThat(jsonValue).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final ThingTag underTest = ThingTag.fromJson(KNOWN_JSON);

        Assertions.assertThat(underTest).isNotNull();
        Assertions.assertThat(underTest.getId()).isEqualTo(TestConstants.Thing.THING_ID);
        Assertions.assertThat(underTest.getRevision()).isEqualTo(TestConstants.Thing.REVISION_NUMBER);
    }

}
