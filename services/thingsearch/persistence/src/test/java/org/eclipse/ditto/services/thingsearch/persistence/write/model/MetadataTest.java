/*
 * Copyright (c) 2019 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.services.thingsearch.persistence.write.model;

import static org.mutabilitydetector.unittesting.AllowedReason.assumingFields;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import javax.annotation.Nullable;

import org.eclipse.ditto.model.policies.PolicyId;
import org.eclipse.ditto.model.things.ThingId;
import org.junit.After;
import org.junit.Test;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link Metadata}.
 */
public final class MetadataTest extends AbstractWithActorSystemTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(Metadata.class,
                areImmutable(),
                provided(ThingId.class, PolicyId.class).areAlsoImmutable(),
                assumingFields("senders").areSafelyCopiedUnmodifiableCollectionsWithImmutableElements());
    }

    @Test
    public void testHashCodeAndEquals() {
        system = ActorSystem.create();
        final TestProbe probe1 = TestProbe.apply(system);
        final TestProbe probe2 = TestProbe.apply(system);
        EqualsVerifier.forClass(Metadata.class)
                .usingGetClass()
                .withPrefabValues(ActorRef.class, probe1.ref(), probe2.ref())
                .verify();
    }

}