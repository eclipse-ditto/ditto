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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.services.utils.distributedcache.actors.ReadConsistency;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;

import akka.actor.ActorRef;
import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link LookupEnforcerTest}.
 */
public final class LookupEnforcerTest {

    private static final String ID = "RickSanchez";
    private static final ReadConsistency READ_CONSISTENCY = ReadConsistency.LOCAL;

    private static LookupContext<?> lookupContext;

    private LookupEnforcer underTest;

    /** */
    @SuppressWarnings("unchecked")
    @BeforeClass
    public static void initTestConstants() {
        lookupContext = LookupContext.getInstance(Mockito.mock(ThingCommand.class), Mockito.mock(ActorRef.class),
                Mockito.mock(ActorRef.class));
    }

    /** */
    @Before
    public void setUp() {
        underTest = new LookupEnforcer(ID, lookupContext, READ_CONSISTENCY);
    }

    /** */
    @Test
    public void assertImmutability() {
        assertInstancesOf(LookupEnforcer.class,
                areImmutable(),
                provided(LookupContext.class, ReadConsistency.class).areAlsoImmutable());
    }

    /** */
    @Ignore("EqualsVerifier cannot cope with type ActorRef")
    @Test
    public void testHashCodeAndEquals() {
        final LookupEnforcer red = new LookupEnforcer(ID,
                LookupContext.getInstance(Mockito.mock(ThingCommand.class), Mockito.mock(ActorRef.class),
                        Mockito.mock(ActorRef.class)), ReadConsistency.LOCAL);
        final LookupEnforcer black = new LookupEnforcer("Foo",
                LookupContext.getInstance(Mockito.mock(PolicyCommand.class), Mockito.mock(ActorRef.class),
                        Mockito.mock(ActorRef.class)), ReadConsistency.MAJORITY);

        EqualsVerifier.forClass(LookupEnforcer.class)
                .usingGetClass()
                .withPrefabValues(LookupEnforcer.class, red, black)
                .verify();
    }

    /** */
    @Test
    public void tryToCreateInstanceWithNullId() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new LookupEnforcer(null, lookupContext, READ_CONSISTENCY))
                .withMessage("The %s must not be null!", "ID")
                .withNoCause();
    }

    /** */
    @Test
    public void tryToCreateInstanceWithEmptyId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new LookupEnforcer("", lookupContext, READ_CONSISTENCY))
                .withMessage("The argument '%s' must not be empty!", "ID")
                .withNoCause();
    }

    /** */
    @Test
    public void tryToCreateInstanceWithNullContext() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new LookupEnforcer(ID, null, READ_CONSISTENCY))
                .withMessage("The %s must not be null!", "Lookup Context")
                .withNoCause();
    }

    /** */
    @Test
    public void tryToCreateInstanceWithNullReadConsistency() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new LookupEnforcer(ID, lookupContext, null))
                .withMessage("The %s must not be null!", "Read Consistency")
                .withNoCause();
    }

    /** */
    @Test
    public void getIdReturnsExpected() {
        final String id = underTest.getId();

        assertThat(id).isEqualTo(ID);
    }

    /** */
    @Test
    public void getContextReturnsExpected() {
        final LookupContext<?> context = underTest.getContext();

        assertThat(context).isEqualTo(lookupContext);
    }

    /** */
    @Test
    public void getReadConsistencyReturnsExpected() {
        final ReadConsistency readConsistency = underTest.getReadConsistency();

        assertThat((Object) readConsistency).isEqualTo(READ_CONSISTENCY);
    }

}
