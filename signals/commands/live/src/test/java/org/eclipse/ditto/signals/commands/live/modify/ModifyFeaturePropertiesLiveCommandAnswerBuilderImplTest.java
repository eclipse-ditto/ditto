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
import org.eclipse.ditto.signals.commands.things.exceptions.FeaturePropertiesNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeaturePropertiesNotModifiableException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit test for {@link ModifyFeaturePropertiesLiveCommandAnswerBuilderImpl}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class ModifyFeaturePropertiesLiveCommandAnswerBuilderImplTest {

    @Mock
    private ModifyFeaturePropertiesLiveCommand commandMock;

    private ModifyFeaturePropertiesLiveCommandAnswerBuilderImpl underTest;

    /** */
    @Before
    public void setUp() {
        Mockito.when(commandMock.getThingId()).thenReturn(TestConstants.Thing.THING_ID);
        Mockito.when(commandMock.getDittoHeaders()).thenReturn(DittoHeaders.empty());
        Mockito.when(commandMock.getFeatureId()).thenReturn(TestConstants.Feature.FLUX_CAPACITOR_ID);
        Mockito.when(commandMock.getProperties()).thenReturn(TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES);

        underTest = ModifyFeaturePropertiesLiveCommandAnswerBuilderImpl.newInstance(commandMock);
    }

    /** */
    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToGetNewInstanceWithNullCommand() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> ModifyFeaturePropertiesLiveCommandAnswerBuilderImpl.newInstance(null))
                .withMessage(MessageFormat.format("The {0} must not be null!", "command"))
                .withNoCause();
    }

    /** */
    @Test
    public void buildAnswerWithModifyFeaturePropertiesCreatedResponseOnly() {
        final LiveCommandAnswer liveCommandAnswer =
                underTest.withResponse(ModifyFeaturePropertiesLiveCommandAnswerBuilder.ResponseFactory::created)
                        .withoutEvent()
                        .build();

        assertThat(liveCommandAnswer)
                .hasNoEvent()
                .hasThingModifyCommandResponse();
    }

    /** */
    @Test
    public void buildAnswerWithModifyFeaturePropertiesModifiedResponseOnly() {
        final LiveCommandAnswer liveCommandAnswer =
                underTest.withResponse(ModifyFeaturePropertiesLiveCommandAnswerBuilder.ResponseFactory::modified)
                        .withoutEvent()
                        .build();

        assertThat(liveCommandAnswer)
                .hasNoEvent()
                .hasThingModifyCommandResponse();
    }

    /** */
    @Test
    public void buildAnswerWithFeaturePropertiesNotAccessibleErrorResponseOnly() {
        final LiveCommandAnswer liveCommandAnswer =
                underTest.withResponse(ModifyFeaturePropertiesLiveCommandAnswerBuilder
                        .ResponseFactory::featurePropertiesNotAccessibleError)
                        .withoutEvent()
                        .build();

        assertThat(liveCommandAnswer)
                .hasNoEvent()
                .hasThingErrorResponse()
                .withType(ThingErrorResponse.TYPE)
                .withDittoHeaders(DittoHeaders.empty())
                .withStatus(HttpStatusCode.NOT_FOUND)
                .withDittoRuntimeExceptionOfType(FeaturePropertiesNotAccessibleException.class);
    }

    /** */
    @Test
    public void buildAnswerWithFeaturePropertiesNotModifiableErrorResponseOnly() {
        final LiveCommandAnswer liveCommandAnswer =
                underTest.withResponse(ModifyFeaturePropertiesLiveCommandAnswerBuilder
                        .ResponseFactory::featurePropertiesNotModifiableError)
                        .withoutEvent()
                        .build();

        assertThat(liveCommandAnswer)
                .hasNoEvent()
                .hasThingErrorResponse()
                .withType(ThingErrorResponse.TYPE)
                .withDittoHeaders(DittoHeaders.empty())
                .withStatus(HttpStatusCode.FORBIDDEN)
                .withDittoRuntimeExceptionOfType(FeaturePropertiesNotModifiableException.class);
    }

    /** */
    @Test
    public void buildAnswerWithFeaturePropertiesCreatedEventOnly() {
        final LiveCommandAnswer liveCommandAnswer = underTest.withoutResponse()
                .withEvent(ModifyFeaturePropertiesLiveCommandAnswerBuilder.EventFactory::created)
                .build();

        assertThat(liveCommandAnswer)
                .hasNoResponse()
                .hasThingModifiedEvent();
    }

    /** */
    @Test
    public void buildAnswerWithFeaturePropertiesModifiedEventOnly() {
        final LiveCommandAnswer liveCommandAnswer = underTest.withoutResponse()
                .withEvent(ModifyFeaturePropertiesLiveCommandAnswerBuilder.EventFactory::modified)
                .build();

        assertThat(liveCommandAnswer)
                .hasNoResponse()
                .hasThingModifiedEvent();
    }

}
