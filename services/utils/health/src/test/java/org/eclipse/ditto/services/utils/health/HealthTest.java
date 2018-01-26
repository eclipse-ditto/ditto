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
import static org.eclipse.ditto.services.utils.health.PersistenceClusterHealth.CLUSTER;
import static org.eclipse.ditto.services.utils.health.PersistenceClusterHealth.PERSISTENCE;
import static org.eclipse.ditto.services.utils.health.Health.JSON_FIELD_STATUS;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.ditto.json.JsonKey;
import org.junit.Test;
import org.mutabilitydetector.unittesting.MutabilityAssert;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link Health}.
 */
public final class HealthTest {


    @Test
    public void assertImmutability() {
        MutabilityAssert.assertInstancesOf(Health.class, areImmutable());
    }


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(Health.class).usingGetClass().verify();
    }


    @Test
    public void jsonSerializationFromNewInstance() {
        final Health health = PersistenceClusterHealth.newInstance();
        assertHealthContainsExactly(health, JSON_FIELD_STATUS);
    }


    @Test
    public void jsonSerializationWithEmptyHealth() {
        final Health health = PersistenceClusterHealth.of(null, null);
        assertHealthContainsExactly(health, JSON_FIELD_STATUS);
    }


    @Test
    public void jsonSerializationWithOnlyPersistence() {
        final Health health = PersistenceClusterHealth.of(HealthStatus.of(HealthStatus.Status.UP), null);
        assertHealthContainsExactly(health, JSON_FIELD_STATUS, PERSISTENCE);
    }


    @Test
    public void jsonSerializationWithOnlyCluster() {
        final Health health = PersistenceClusterHealth.of(null, HealthStatus.of(HealthStatus.Status.UP));
        assertHealthContainsExactly(health, JSON_FIELD_STATUS, CLUSTER);
    }


    @Test
    public void jsonSerializationWithAll() {
        final Health health = PersistenceClusterHealth.of(HealthStatus.of(HealthStatus.Status.UP),
                HealthStatus.of(HealthStatus.Status.UP));

        assertHealthContainsExactly(health, JSON_FIELD_STATUS, PERSISTENCE, CLUSTER);
    }

    private void assertHealthContainsExactly(final Health health, final String... fieldDefinitions) {
        final Set<JsonKey> actualJsonPointers = new HashSet<>(health.toJson().asObject().getKeys());
        final Set<JsonKey> expectedJsonKeys = Stream.of(fieldDefinitions)
                .map(JsonKey::of)
                .collect(Collectors.toSet());
        assertThat(actualJsonPointers).isEqualTo(expectedJsonKeys);
    }

}
