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

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.text.MessageFormat;

import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.live.assertions.LiveCommandAssertions;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswer;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.FeaturePropertyNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeaturePropertyNotModifiableException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit test for {@link ModifyFeaturePropertyLiveCommandAnswerBuilderImpl}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class ModifyFeaturePropertyLiveCommandAnswerBuilderImplTest {

    @Mock
    private ModifyFeaturePropertyLiveCommand commandMock;

    private ModifyFeaturePropertyLiveCommandAnswerBuilderImpl underTest;

    /** */
    @Before
    public void setUp() {
        Mockito.when(commandMock.getThingId()).thenReturn(TestConstants.Thing.THING_ID);
        Mockito.when(commandMock.getDittoHeaders()).thenReturn(DittoHeaders.empty());
        Mockito.when(commandMock.getFeatureId()).thenReturn(TestConstants.Feature.FLUX_CAPACITOR_ID);
        Mockito.when(commandMock.getPropertyPointer())
                .thenReturn(TestConstants.Feature.FLUX_CAPACITOR_PROPERTY_POINTER);
        Mockito.when(commandMock.getPropertyValue()).thenReturn(TestConstants.Feature.FLUX_CAPACITOR_PROPERTY_VALUE);

        underTest = ModifyFeaturePropertyLiveCommandAnswerBuilderImpl.newInstance(commandMock);
    }

    /** */
    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToGetNewInstanceWithNullCommand() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ModifyFeaturePropertyLiveCommandAnswerBuilderImpl.newInstance(null))
                .withMessage(MessageFormat.format("The {0} must not be null!", "command"))
                .withNoCause();
    }

    /** */
    @Test
    public void buildAnswerWithModifyFeaturePropertyCreatedResponseOnly() {
        final LiveCommandAnswer liveCommandAnswer =
                underTest.withResponse(ModifyFeaturePropertyLiveCommandAnswerBuilder.ResponseFactory::created)
                        .withoutEvent()
                        .build();

        LiveCommandAssertions.assertThat(liveCommandAnswer)
                .hasNoEvent()
                .hasThingModifyCommandResponse();
    }

    /** */
    @Test
    public void buildAnswerWithModifyFeaturePropertyModifiedResponseOnly() {
        final LiveCommandAnswer liveCommandAnswer =
                underTest.withResponse(ModifyFeaturePropertyLiveCommandAnswerBuilder.ResponseFactory::modified)
                        .withoutEvent()
                        .build();

        LiveCommandAssertions.assertThat(liveCommandAnswer)
                .hasNoEvent()
                .hasThingModifyCommandResponse();
    }

    /** */
    @Test
    public void buildAnswerWithFeaturePropertyNotAccessibleErrorResponseOnly() {
        final LiveCommandAnswer liveCommandAnswer =
                underTest.withResponse(ModifyFeaturePropertyLiveCommandAnswerBuilder
                        .ResponseFactory::featurePropertyNotAccessibleError)
                        .withoutEvent()
                        .build();

        LiveCommandAssertions.assertThat(liveCommandAnswer)
                .hasNoEvent()
                .hasThingErrorResponse()
                .withType(ThingErrorResponse.TYPE)
                .withDittoHeaders(DittoHeaders.empty())
                .withStatus(HttpStatusCode.NOT_FOUND)
                .withDittoRuntimeExceptionOfType(FeaturePropertyNotAccessibleException.class);
    }

    /** */
    @Test
    public void buildAnswerWithFeaturePropertyNotModifiableErrorResponseOnly() {
        final LiveCommandAnswer liveCommandAnswer =
                underTest.withResponse(ModifyFeaturePropertyLiveCommandAnswerBuilder
                        .ResponseFactory::featurePropertyNotModifiableError)
                        .withoutEvent()
                        .build();

        LiveCommandAssertions.assertThat(liveCommandAnswer)
                .hasNoEvent()
                .hasThingErrorResponse()
                .withType(ThingErrorResponse.TYPE)
                .withDittoHeaders(DittoHeaders.empty())
                .withStatus(HttpStatusCode.FORBIDDEN)
                .withDittoRuntimeExceptionOfType(FeaturePropertyNotModifiableException.class);
    }

    /** */
    @Test
    public void buildAnswerWithFeaturePropertyCreatedEventOnly() {
        final LiveCommandAnswer liveCommandAnswer = underTest.withoutResponse()
                .withEvent(ModifyFeaturePropertyLiveCommandAnswerBuilder.EventFactory::created)
                .build();

        LiveCommandAssertions.assertThat(liveCommandAnswer)
                .hasNoResponse()
                .hasThingModifiedEvent();
    }

    /** */
    @Test
    public void buildAnswerWithFeaturePropertyModifiedEventOnly() {
        final LiveCommandAnswer liveCommandAnswer = underTest.withoutResponse()
                .withEvent(ModifyFeaturePropertyLiveCommandAnswerBuilder.EventFactory::modified)
                .build();

        LiveCommandAssertions.assertThat(liveCommandAnswer)
                .hasNoResponse()
                .hasThingModifiedEvent();
    }

}
