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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.UUID;

import org.eclipse.ditto.base.model.entity.type.EntityType;
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

    private static final ActorSystem actorSystem = ActorSystem.create();

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
        final EntityType thingType = EntityType.of("thing");
        final ActorRef actorRef1 = createTestActorRef("ref1");
        final EntityType policyType = EntityType.of("policy");
        final ActorRef actorRef2 = createTestActorRef("ref2");

        // WHEN
        final EntityRegionMap entityRegionMap = EntityRegionMap.newBuilder()
                .put(thingType, actorRef1)
                .put(policyType, actorRef2)
                .build();

        // WHEN
        assertThat(entityRegionMap).isNotNull();
        assertThat(entityRegionMap.lookup(thingType)).contains(actorRef1);
        assertThat(entityRegionMap.lookup(policyType)).contains(actorRef2);
        assertThat(entityRegionMap.lookup(EntityType.of("doesNotExist"))).isEmpty();
    }

    @Test(expected = NullPointerException.class)
    public void buildWithNullResourceTypeThrowsException() {
        EntityRegionMap.newBuilder().put(null, createTestActorRef("ref"));
    }

    @Test(expected = NullPointerException.class)
    public void buildWithNullActorRefThrowsException() {
        EntityRegionMap.newBuilder().put(EntityType.of("thing"), null);
    }

    private static ActorRef createTestActorRef(final String actorNamePrefix) {
        return new TestProbe(actorSystem, actorNamePrefix + "-" + UUID.randomUUID()).ref();
    }


}
