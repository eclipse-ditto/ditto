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
package org.eclipse.ditto.internal.utils.cacheloaders;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Objects;

import javax.annotation.Nullable;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link EnforcementCacheKey}.
 */
public final class EnforcementCacheKeyTest {

    private static final EntityType THING_TYPE = EntityType.of("thing");
    private static final EntityId ENTITY_ID = EntityId.of(THING_TYPE, "entity:id");
    private static final EnforcementCacheKey CACHE_KEY = EnforcementCacheKey.of(ENTITY_ID);
    private static final String EXPECTED_SERIALIZED_ENTITY_ID =
            String.join(EnforcementCacheKey.DELIMITER, THING_TYPE, ENTITY_ID);

    @Test
    public void assertImmutability() {
        assertInstancesOf(EnforcementCacheKey.class,
                areImmutable(),
                provided(EntityId.class, EnforcementContext.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(EnforcementCacheKey.class)
                .withIgnoredFields("context")
                .withPrefabValues(EntityId.class,
                        new OtherEntityIdImplementation("blue"),
                        new OtherEntityIdImplementation("green"))
                .verify();
    }

    @Test
    public void testSerialization() {
        // check preconditions
        assertThat(CACHE_KEY).isNotNull();
        assertThat((CharSequence) CACHE_KEY.getId()).isEqualTo(ENTITY_ID);

        // assert serialization
        assertThat(CACHE_KEY.toString()).isEqualTo(EXPECTED_SERIALIZED_ENTITY_ID);
    }

    @Test
    public void testDeserialization() {
        assertThat(EnforcementCacheKey.readFrom(EXPECTED_SERIALIZED_ENTITY_ID)).isEqualTo(CACHE_KEY);
    }

    @Test
    public void testSerializationWithDifferentType() {
        final var otherImplementation = new OtherEntityIdImplementation("entity:id");
        final var originalCacheKey = EnforcementCacheKey.of(otherImplementation);

        final var serialized = originalCacheKey.toString();
        final var deserialized = EnforcementCacheKey.readFrom(serialized);

        assertThat(deserialized).isEqualTo(originalCacheKey);
    }

    /**
     * Implementation of {@link EntityId} to support verifying serialization and deserialization are working properly.
     */
    private static final class OtherEntityIdImplementation implements EntityId {

        private final String id;

        private OtherEntityIdImplementation(final String id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return id;
        }

        @Override
        public boolean equals(@Nullable final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) return false;
            final var that = (OtherEntityIdImplementation) o;
            return Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        @Override
        public EntityType getEntityType() {
            return THING_TYPE;
        }

    }

}
