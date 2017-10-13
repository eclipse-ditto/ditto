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
package org.eclipse.ditto.signals.commands.live.modify;

import static org.eclipse.ditto.signals.commands.live.assertions.LiveCommandAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.text.MessageFormat;

import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswer;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingConflictException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit test for {@link CreateThingLiveCommandAnswerBuilderImpl}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class CreateThingLiveCommandAnswerBuilderImplTest {

    @Mock
    private CreateThingLiveCommand commandMock;

    private CreateThingLiveCommandAnswerBuilderImpl underTest;

    /** */
    @Before
    public void setUp() {
        Mockito.when(commandMock.getThingId()).thenReturn(TestConstants.Thing.THING_ID);
        Mockito.when(commandMock.getThing()).thenReturn(TestConstants.Thing.THING);
        Mockito.when(commandMock.getDittoHeaders()).thenReturn(DittoHeaders.empty());

        underTest = CreateThingLiveCommandAnswerBuilderImpl.newInstance(commandMock);
    }

    /** */
    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToGetNewInstanceWithNullCommand() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> CreateThingLiveCommandAnswerBuilderImpl.newInstance(null))
                .withMessage(MessageFormat.format("The {0} must not be null!", "command"))
                .withNoCause();
    }

    /** */
    @Test
    public void buildAnswerWithThingCreatedResponseOnly() {
        final LiveCommandAnswer liveCommandAnswer =
                underTest.withResponse(CreateThingLiveCommandAnswerBuilder.ResponseFactory::created)
                        .withoutEvent()
                        .build();

        assertThat(liveCommandAnswer)
                .hasNoEvent()
                .hasThingModifyCommandResponse();
    }

    /** */
    @Test
    public void buildAnswerWithThingConflictErrorResponseOnly() {
        final LiveCommandAnswer liveCommandAnswer =
                underTest.withResponse(CreateThingLiveCommandAnswerBuilder.ResponseFactory::thingConflictError)
                        .withoutEvent()
                        .build();

        assertThat(liveCommandAnswer)
                .hasNoEvent()
                .hasThingErrorResponse()
                .withType(ThingErrorResponse.TYPE)
                .withDittoHeaders(DittoHeaders.empty())
                .withStatus(HttpStatusCode.CONFLICT)
                .withDittoRuntimeExceptionOfType(ThingConflictException.class);
    }

    /** */
    @Test
    public void buildAnswerWithThingCreatedEventOnly() {
        final LiveCommandAnswer liveCommandAnswer = underTest.withoutResponse()
                .withEvent(CreateThingLiveCommandAnswerBuilder.EventFactory::created)
                .build();

        assertThat(liveCommandAnswer)
                .hasNoResponse()
                .hasThingModifiedEvent();
    }

}
