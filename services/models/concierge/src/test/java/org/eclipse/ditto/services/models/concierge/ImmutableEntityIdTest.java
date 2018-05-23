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
package org.eclipse.ditto.services.models.concierge;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link ImmutableEntityId}.
 */
public class ImmutableEntityIdTest {

    private static final String RESOURCE_TYPE = "resource-type";
    private static final String ENTITY_ID_WITHOUT_TYPE = "entity:id";
    private static final EntityId ENTITY_ID = ImmutableEntityId.of(RESOURCE_TYPE, ENTITY_ID_WITHOUT_TYPE);
    private static final String EXPECTED_SERIALIZED_ENTITY_ID =
            String.join(ImmutableEntityId.DELIMITER, RESOURCE_TYPE, ENTITY_ID_WITHOUT_TYPE);
    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableEntityId.class, areImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableEntityId.class).verify();
    }

    @Test
    public void testSerialization() {
        // check preconditions
        assertThat(ENTITY_ID).isNotNull();
        assertThat(ENTITY_ID.getResourceType()).isEqualTo(RESOURCE_TYPE);
        assertThat(ENTITY_ID.getId()).isEqualTo(ENTITY_ID_WITHOUT_TYPE);

        // assert serialization
        assertThat(ENTITY_ID.toString()).isEqualTo(EXPECTED_SERIALIZED_ENTITY_ID);
    }

    @Test
    public void testDeserialization() {
        assertThat(ImmutableEntityId.readFrom(EXPECTED_SERIALIZED_ENTITY_ID)).isEqualTo(ENTITY_ID);
    }

}
