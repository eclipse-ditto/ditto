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

import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.signals.commands.base.CommandResponse;
import org.eclipse.ditto.signals.events.base.Event;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.eclipse.ditto.signals.commands.live.assertions.LiveCommandAssertions;

/**
 * Unit test for {@link LiveCommandAnswerFactory}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class LiveCommandAnswerFactoryTest {

    @Mock
    private CommandResponse<?> commandResponseMock;

    @Mock
    private Event<?> eventMock;

    /** */
    @Test
    public void assertImmutability() {
        assertInstancesOf(LiveCommandAnswerFactory.class, areImmutable());
    }

    /** */
    @Test
    public void getLiveCommandAnswerInstanceWithCommandResponseOnly() {
        final LiveCommandAnswer liveCommandAnswer = LiveCommandAnswerFactory.newLiveCommandAnswer(commandResponseMock);

        LiveCommandAssertions.assertThat(liveCommandAnswer)
                .hasResponse(commandResponseMock)
                .hasNoEvent();
    }

    /** */
    @Test
    public void getLiveCommandAnswerInstanceWithNullCommandResponse() {
        final LiveCommandAnswer liveCommandAnswer =
                LiveCommandAnswerFactory.newLiveCommandAnswer((CommandResponse) null);

        LiveCommandAssertions.assertThat(liveCommandAnswer)
                .hasNoResponse()
                .hasNoEvent();
    }

    /** */
    @Test
    public void getLiveCommandAnswerInstanceWithEventOnly() {
        final LiveCommandAnswer liveCommandAnswer = LiveCommandAnswerFactory.newLiveCommandAnswer(eventMock);

        LiveCommandAssertions.assertThat(liveCommandAnswer)
                .hasNoResponse()
                .hasEvent(eventMock);
    }

    /** */
    @Test
    public void getLiveCommandAnswerInstanceWithNullEvent() {
        final LiveCommandAnswer liveCommandAnswer = LiveCommandAnswerFactory.newLiveCommandAnswer((Event) null);

        LiveCommandAssertions.assertThat(liveCommandAnswer)
                .hasNoResponse()
                .hasNoEvent();
    }

    /** */
    @Test
    public void getLiveCommandAnswerInstanceWithCommandResponseAndEvent() {
        final LiveCommandAnswer liveCommandAnswer = LiveCommandAnswerFactory.newLiveCommandAnswer(commandResponseMock,
                eventMock);

        LiveCommandAssertions.assertThat(liveCommandAnswer)
                .hasResponse(commandResponseMock)
                .hasEvent(eventMock);
    }

    /** */
    @Test
    public void getLiveCommandAnswerInstanceWithNullCommandResponseAndNullEvent() {
        final LiveCommandAnswer liveCommandAnswer =
                LiveCommandAnswerFactory.newLiveCommandAnswer((CommandResponse) null,
                        (Event) null);

        LiveCommandAssertions.assertThat(liveCommandAnswer)
                .hasNoResponse()
                .hasNoEvent();
    }

}