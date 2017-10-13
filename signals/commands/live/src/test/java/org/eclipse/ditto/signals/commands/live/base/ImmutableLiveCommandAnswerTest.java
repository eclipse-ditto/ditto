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
package org.eclipse.ditto.signals.commands.live.base;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.commands.things.ThingCommandResponse;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.junit.Test;
import org.mockito.Mockito;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ImmutableLiveCommandAnswer}.
 */
public final class ImmutableLiveCommandAnswerTest {

    /** */
    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableLiveCommandAnswer.class,
                areImmutable(),
                provided(CommandResponse.class, Event.class).areAlsoImmutable());
    }

    /** */
    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableLiveCommandAnswer.class) //
                .usingGetClass() //
                .verify();
    }

    /** */
    @Test
    public void getResponseReturnsEmptyOptionalForNullResponse() {
        final ImmutableLiveCommandAnswer underTest = ImmutableLiveCommandAnswer.newInstance(null, null);

        assertThat(underTest.getResponse()).isEmpty();
    }

    /** */
    @Test
    public void getEventReturnsEmptyOptionalForNullEvent() {
        final ImmutableLiveCommandAnswer underTest = ImmutableLiveCommandAnswer.newInstance(null, null);

        assertThat(underTest.getEvent()).isEmpty();
    }

    /** */
    @Test
    public void getResponseReturnsExpected() {
        final ThingCommandResponse responseMock = Mockito.mock(ThingCommandResponse.class);
        final ImmutableLiveCommandAnswer underTest = ImmutableLiveCommandAnswer.newInstance(responseMock, null);

        assertThat(underTest.getResponse()).contains(responseMock);
    }

    /** */
    @Test
    public void getEventReturnsExpected() {
        final ThingEvent eventMock = Mockito.mock(ThingEvent.class);
        final ImmutableLiveCommandAnswer underTest = ImmutableLiveCommandAnswer.newInstance(null, eventMock);

        assertThat(underTest.getEvent()).contains(eventMock);
    }

}