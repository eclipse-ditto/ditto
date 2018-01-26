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
package org.eclipse.ditto.services.utils.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.json.JsonFieldDefinition;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonPointer;
import org.junit.Test;
import org.mutabilitydetector.unittesting.MutabilityAssert;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ClusterPersistenceHealth}.
 */
public final class HealthTest {


    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(ClusterPersistenceHealth.class, areImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ClusterPersistenceHealth.class).usingGetClass().verify();
    }


    @Test
    public void jsonSerializationWithEmptyHealth() {
        final ClusterPersistenceHealth health = ClusterPersistenceHealth.of(null, null);

        assertHealthContainsExactly(health, HealthStatus.JSON_KEY_STATUS);
    }


    @Test
    public void jsonSerializationWithOnlyPersistence() {
        final ClusterPersistenceHealth
                health = ClusterPersistenceHealth.of(HealthStatus.of(HealthStatus.Status.UP), null);

        assertHealthContainsExactly(health, HealthStatus.JSON_KEY_STATUS, ClusterPersistenceHealth.JSON_KEY_PERSISTENCE);
    }


    @Test
    public void jsonSerializationWithOnlyCluster() {
        final ClusterPersistenceHealth health = ClusterPersistenceHealth.of(null, HealthStatus.of(HealthStatus.Status.UP));

        assertHealthContainsExactly(health, HealthStatus.JSON_KEY_STATUS, ClusterPersistenceHealth.JSON_KEY_CLUSTER);

    }


    @Test
    public void jsonSerializationWithAll() {
        final ClusterPersistenceHealth health = ClusterPersistenceHealth.of(HealthStatus.of(HealthStatus.Status.UP),
                HealthStatus.of(HealthStatus.Status.UP));

        assertHealthContainsExactly(health, HealthStatus.JSON_KEY_STATUS,
                ClusterPersistenceHealth.JSON_KEY_PERSISTENCE, ClusterPersistenceHealth.JSON_KEY_CLUSTER);
    }

    private void assertHealthContainsExactly(final ClusterPersistenceHealth health, final JsonFieldDefinition... fieldDefinitions) {
        final Set<JsonKey> actualJsonPointers = new HashSet<>(health.toJson().asObject().getKeys());
        final Set<JsonKey> expectedJsonKeys = Stream.of(fieldDefinitions)
                .map(JsonFieldDefinition::getPointer)
                .map(JsonPointer::getRoot)
                .map(Optional::get)
                .collect(Collectors.toSet());
        assertThat(actualJsonPointers).isEqualTo(expectedJsonKeys);
    }

}
