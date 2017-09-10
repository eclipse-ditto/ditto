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
package org.eclipse.ditto.services.gateway.proxy.actors;

import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.signals.base.Signal;
import org.junit.Ignore;
import org.junit.Test;

import akka.actor.ActorRef;
import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link LookupContextTest}.
 */
public final class LookupContextTest {

    /** */
    @Test
    public void assertImmutability() {
        assertInstancesOf(LookupContext.class,
                areImmutable(),
                provided(ActorRef.class, Signal.class).areAlsoImmutable());
    }

    /** */
    @Ignore("EqualsVerifier cannot cope with type ActorRef.")
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(LookupContext.class)
                .usingGetClass()
                .verify();
    }

}
