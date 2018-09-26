/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.concierge.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.UUID;

import org.junit.AfterClass;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.TestProbe;
import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link EntityRegionMap}.
 */
public final class EntityRegionMapTest {

    private static ActorSystem actorSystem = ActorSystem.create();

    @AfterClass
    public static void tearDown() {
        actorSystem.terminate();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(EntityRegionMap.class, areImmutable(),
                provided(ActorRef.class).isAlsoImmutable(),
                assumingFields("rawMap").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }


    @Test
    public void testHashCodeAndEquals() {
        final ActorRef red = createTestActorRef("red");
        final ActorRef black = createTestActorRef("black");

        EqualsVerifier.forClass(EntityRegionMap.class)
                .withPrefabValues(ActorRef.class, red, black)
                .verify();
    }

    @Test
    public void buildAndLookup() {
        // GIVEN
        final String resourceType1 = "resourceType1";
        final ActorRef actorRef1 = createTestActorRef("ref1");
        final String resourceType2 = "resourceType2";
        final ActorRef actorRef2 = createTestActorRef("ref2");

        // WHEN
        final EntityRegionMap entityRegionMap = EntityRegionMap.newBuilder()
                .put(resourceType1, actorRef1)
                .put(resourceType2, actorRef2)
                .build();

        // WHEN
        assertThat(entityRegionMap).isNotNull();
        assertThat(entityRegionMap.lookup(resourceType1)).contains(actorRef1);
        assertThat(entityRegionMap.lookup(resourceType2)).contains(actorRef2);
        assertThat(entityRegionMap.lookup("doesNotExist")).isEmpty();
    }

    @Test(expected = NullPointerException.class)
    public void buildWithNullResourceTypeThrowsException() {
        EntityRegionMap.newBuilder()
                .put(null, createTestActorRef("ref"));
    }

    @Test(expected = NullPointerException.class)
    public void buildWithNullActorRefThrowsException() {
        EntityRegionMap.newBuilder()
                .put("resourceType", null);
    }

    private static ActorRef createTestActorRef(final String actorNamePrefix) {
        return new TestProbe(actorSystem, actorNamePrefix + "-" + UUID.randomUUID()).ref();
    }


}
