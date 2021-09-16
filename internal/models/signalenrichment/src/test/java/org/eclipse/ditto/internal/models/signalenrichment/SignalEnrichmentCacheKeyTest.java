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
package org.eclipse.ditto.internal.models.signalenrichment;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Tests {@link SignalEnrichmentCacheKey}.
 */
public final class SignalEnrichmentCacheKeyTest {

    private static final EntityType THING_TYPE = EntityType.of("thing");
    private static final EntityId ENTITY_ID = EntityId.of(THING_TYPE, "entity:id");
    private static final SignalEnrichmentCacheKey CACHE_KEY = SignalEnrichmentCacheKey.of(ENTITY_ID, null);
    private static final String EXPECTED_SERIALIZED_ENTITY_ID =
            String.join(SignalEnrichmentCacheKey.DELIMITER, THING_TYPE, ENTITY_ID);

    @Test
    public void assertImmutability() {
        assertInstancesOf(SignalEnrichmentCacheKey.class,
                areImmutable(),
                provided(EntityId.class, SignalEnrichmentContext.class).isAlsoImmutable());
    }

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(SignalEnrichmentCacheKey.class)
                // this SignalEnrichmentCacheKey (in contrast to 'EnforcementCacheKey') must use the "context" for equals/hashcode
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
