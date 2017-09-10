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
package org.eclipse.ditto.services.utils.distributedcache.actors;

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.junit.Ignore;
import org.junit.Test;

import akka.actor.ActorRef;
import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link RegisterForCacheUpdates}.
 */
public final class RegisterForCacheUpdatesTest {

    @Test
    public void assertImmutability() {
        assertInstancesOf(RegisterForCacheUpdates.class, areImmutable(),
                provided(ActorRef.class).isAlsoImmutable());
    }

    /** */
    @Ignore("ActorRef delegates to an abstract method")
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(RegisterForCacheUpdates.class)
                .usingGetClass()
                .verify();
    }

}
