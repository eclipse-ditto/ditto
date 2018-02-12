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

import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.eclipse.ditto.signals.commands.live.assertions.LiveCommandAssertions.assertThat;

import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswer;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.FeatureDefinitionNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.FeatureDefinitionNotModifiableException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit test for {@link ModifyFeatureDefinitionLiveCommandAnswerBuilderImpl}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class ModifyFeatureDefinitionLiveCommandAnswerBuilderImplTest {

    @Mock
    private ModifyFeatureDefinitionLiveCommand commandMock;

    private ModifyFeatureDefinitionLiveCommandAnswerBuilderImpl underTest;

    @Before
    public void setUp() {
        Mockito.when(commandMock.getThingId()).thenReturn(TestConstants.Thing.THING_ID);
        Mockito.when(commandMock.getDittoHeaders()).thenReturn(DittoHeaders.empty());
        Mockito.when(commandMock.getFeatureId()).thenReturn(TestConstants.Feature.FLUX_CAPACITOR_ID);
        Mockito.when(commandMock.getDefinition()).thenReturn(TestConstants.Feature.FLUX_CAPACITOR_DEFINITION);

        underTest = ModifyFeatureDefinitionLiveCommandAnswerBuilderImpl.newInstance(commandMock);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToGetNewInstanceWithNullCommand() {
        assertThatNullPointerException()
                .isThrownBy(() -> ModifyFeatureDefinitionLiveCommandAnswerBuilderImpl.newInstance(null))
                .withMessage("The %s must not be null!", "command")
                .withNoCause();
    }

    @Test
    public void buildAnswerWithModifyFeatureDefinitionCreatedResponseOnly() {
        final LiveCommandAnswer liveCommandAnswer =
                underTest.withResponse(ModifyFeatureDefinitionLiveCommandAnswerBuilder.ResponseFactory::created)
                        .withoutEvent()
                        .build();

        assertThat(liveCommandAnswer)
                .hasNoEvent()
                .hasThingModifyCommandResponse();
    }

    @Test
    public void buildAnswerWithModifyFeatureDefinitionModifiedResponseOnly() {
        final LiveCommandAnswer liveCommandAnswer =
                underTest.withResponse(ModifyFeatureDefinitionLiveCommandAnswerBuilder.ResponseFactory::modified)
                        .withoutEvent()
                        .build();

        assertThat(liveCommandAnswer)
                .hasNoEvent()
                .hasThingModifyCommandResponse();
    }

    @Test
    public void buildAnswerWithFeatureDefinitionNotAccessibleErrorResponseOnly() {
        final LiveCommandAnswer liveCommandAnswer =
                underTest.withResponse(ModifyFeatureDefinitionLiveCommandAnswerBuilder
                        .ResponseFactory::featureDefinitionNotAccessibleError)
                        .withoutEvent()
                        .build();

        assertThat(liveCommandAnswer)
                .hasNoEvent()
                .hasThingErrorResponse()
                .withType(ThingErrorResponse.TYPE)
                .withDittoHeaders(DittoHeaders.empty())
                .withStatus(HttpStatusCode.NOT_FOUND)
                .withDittoRuntimeExceptionOfType(FeatureDefinitionNotAccessibleException.class);
    }

    @Test
    public void buildAnswerWithFeatureDefinitionNotModifiableErrorResponseOnly() {
        final LiveCommandAnswer liveCommandAnswer =
                underTest.withResponse(ModifyFeatureDefinitionLiveCommandAnswerBuilder
                        .ResponseFactory::featureDefinitionNotModifiableError)
                        .withoutEvent()
                        .build();

        assertThat(liveCommandAnswer)
                .hasNoEvent()
                .hasThingErrorResponse()
                .withType(ThingErrorResponse.TYPE)
                .withDittoHeaders(DittoHeaders.empty())
                .withStatus(HttpStatusCode.FORBIDDEN)
                .withDittoRuntimeExceptionOfType(FeatureDefinitionNotModifiableException.class);
    }

    @Test
    public void buildAnswerWithFeatureDefinitionCreatedEventOnly() {
        final LiveCommandAnswer liveCommandAnswer = underTest.withoutResponse()
                .withEvent(ModifyFeatureDefinitionLiveCommandAnswerBuilder.EventFactory::created)
                .build();

        assertThat(liveCommandAnswer)
                .hasNoResponse()
                .hasThingModifiedEvent();
    }

    @Test
    public void buildAnswerWithFeatureDefinitionModifiedEventOnly() {
        final LiveCommandAnswer liveCommandAnswer = underTest.withoutResponse()
                .withEvent(ModifyFeatureDefinitionLiveCommandAnswerBuilder.EventFactory::modified)
                .build();

        assertThat(liveCommandAnswer)
                .hasNoResponse()
                .hasThingModifiedEvent();
    }

}
