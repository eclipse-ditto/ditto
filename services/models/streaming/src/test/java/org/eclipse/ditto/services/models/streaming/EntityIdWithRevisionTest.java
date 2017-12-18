package org.eclipse.ditto.services.models.streaming;/*
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

import static org.eclipse.ditto.json.assertions.DittoJsonAssertions.assertThat;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.json.assertions.DittoJsonAssertions;
import org.junit.Test;
import org.mutabilitydetector.unittesting.MutabilityAssert;
import org.mutabilitydetector.unittesting.MutabilityMatchers;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link EntityIdWithRevision}.
 */
public final class EntityIdWithRevisionTest {

    private static final String ENTITY_ID = "entity:id:123456789";
    private static final long REVISION_NUMBER = 123456789;

    private static final JsonValue KNOWN_JSON = JsonFactory.newObjectBuilder()
            .set(EntityIdWithRevision.JsonFields.ID, ENTITY_ID)
            .set(EntityIdWithRevision.JsonFields.REVISION, REVISION_NUMBER)
            .set(EntityIdWithRevision.JsonFields.TYPE, EntityIdWithRevision.class.getName())
            .build();

    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(EntityIdWithRevision.class, MutabilityMatchers.areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(EntityIdWithRevision.class) //
                .usingGetClass() //
                .withRedefinedSuperclass() //
                .verify();
    }

    @Test
    public void toJsonReturnsExpected() {
        final EntityIdWithRevision underTest = EntityIdWithRevision.of(ENTITY_ID, REVISION_NUMBER);
        final JsonValue jsonValue = underTest.toJson();

        DittoJsonAssertions.assertThat(jsonValue).isEqualTo(KNOWN_JSON);
    }

    @Test
    public void createInstanceFromValidJson() {
        final EntityIdWithRevision underTest = EntityIdWithRevision.fromJson(KNOWN_JSON.toString());

        assertThat(underTest).isNotNull();
        assertThat(underTest.getId()).isEqualTo(ENTITY_ID);
        assertThat(underTest.getRevision()).isEqualTo(REVISION_NUMBER);
    }
}
