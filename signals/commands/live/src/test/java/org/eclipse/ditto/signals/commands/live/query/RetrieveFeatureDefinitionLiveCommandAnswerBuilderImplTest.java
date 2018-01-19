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
package org.eclipse.ditto.signals.commands.live.query;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.ditto.signals.commands.live.assertions.LiveCommandAssertions.assertThat;

import java.text.MessageFormat;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.FeatureDefinition;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswer;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.FeatureDefinitionNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureDefinitionResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit test for {@link RetrieveFeatureDefinitionLiveCommandAnswerBuilderImpl}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class RetrieveFeatureDefinitionLiveCommandAnswerBuilderImplTest {

    @Mock
    private RetrieveFeatureDefinitionLiveCommand commandMock;

    private RetrieveFeatureDefinitionLiveCommandAnswerBuilderImpl underTest;

    @Before
    public void setUp() {
        Mockito.when(commandMock.getThingId()).thenReturn(TestConstants.Thing.THING_ID);
        Mockito.when(commandMock.getDittoHeaders()).thenReturn(DittoHeaders.empty());
        Mockito.when(commandMock.getFeatureId()).thenReturn(TestConstants.Feature.FLUX_CAPACITOR_ID);

        underTest = RetrieveFeatureDefinitionLiveCommandAnswerBuilderImpl.newInstance(commandMock);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToGetNewInstanceWithNullCommand() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> RetrieveFeatureDefinitionLiveCommandAnswerBuilderImpl.newInstance(null))
                .withMessage(MessageFormat.format("The {0} must not be null!", "command"))
                .withNoCause();
    }

    @Test
    public void buildAnswerWithoutResponse() {
        final LiveCommandAnswer liveCommandAnswer = underTest.withoutResponse().build();

        assertThat(liveCommandAnswer)
                .hasNoResponse()
                .hasNoEvent();
    }

    @Test
    public void buildAnswerWithRetrieveFeatureDefinitionResponseOnly() {
        final FeatureDefinition featureProperties = TestConstants.Feature.FLUX_CAPACITOR_DEFINITION;

        final LiveCommandAnswer liveCommandAnswer =
                underTest.withResponse(responseFactory -> responseFactory.retrieved(featureProperties))
                        .build();

        assertThat(liveCommandAnswer)
                .hasNoEvent()
                .hasThingQueryCommandResponse()
                .hasType(RetrieveFeatureDefinitionResponse.TYPE)
                .hasDittoHeaders(DittoHeaders.empty())
                .hasResourcePath(JsonPointer.of("features/" + TestConstants.Feature.FLUX_CAPACITOR_ID + "/definition"));
    }

    @Test
    public void buildAnswerWithFeatureDefinitionNotAccessibleErrorResponseOnly() {
        final LiveCommandAnswer liveCommandAnswer =
                underTest.withResponse(
                        RetrieveFeatureDefinitionLiveCommandAnswerBuilder.ResponseFactory::featureDefinitionNotAccessibleError)
                        .build();

        assertThat(liveCommandAnswer)
                .hasNoEvent()
                .hasThingErrorResponse()
                .withType(ThingErrorResponse.TYPE)
                .withDittoHeaders(DittoHeaders.empty())
                .withStatus(HttpStatusCode.NOT_FOUND)
                .withDittoRuntimeExceptionOfType(FeatureDefinitionNotAccessibleException.class);
    }

}
