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
package org.eclipse.ditto.services.utils.cache;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.entity.id.DefaultEntityId;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link ImmutableEntityIdWithResourceType}.
 */
public class ImmutableEntityIdWithResourceTypeTest {

    private static final String RESOURCE_TYPE = "resource-type";
    private static final EntityId ENTITY_ID_WITHOUT_TYPE = DefaultEntityId.of("entity:id");
    private static final EntityIdWithResourceType ENTITY_ID = ImmutableEntityIdWithResourceType.of(RESOURCE_TYPE, ENTITY_ID_WITHOUT_TYPE);
    private static final String EXPECTED_SERIALIZED_ENTITY_ID =
            String.join(ImmutableEntityIdWithResourceType.DELIMITER, RESOURCE_TYPE, ENTITY_ID_WITHOUT_TYPE);
    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableEntityIdWithResourceType.class,
                areImmutable(),
                provided(EntityId.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableEntityIdWithResourceType.class).verify();
    }

    @Test
    public void testSerialization() {
        // check preconditions
        assertThat(ENTITY_ID).isNotNull();
        assertThat(ENTITY_ID.getResourceType()).isEqualTo(RESOURCE_TYPE);
        assertThat((CharSequence) ENTITY_ID.getId()).isEqualTo(ENTITY_ID_WITHOUT_TYPE);

        // assert serialization
        assertThat(ENTITY_ID.toString()).isEqualTo(EXPECTED_SERIALIZED_ENTITY_ID);
    }

    @Test
    public void testDeserialization() {
        assertThat(ImmutableEntityIdWithResourceType.readFrom(EXPECTED_SERIALIZED_ENTITY_ID)).isEqualTo(ENTITY_ID);
    }

}
