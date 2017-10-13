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

import static org.eclipse.ditto.signals.commands.live.assertions.LiveCommandAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.text.MessageFormat;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswer;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.ThingErrorResponse;
import org.eclipse.ditto.signals.commands.things.exceptions.FeaturePropertiesNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturePropertiesResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit test for {@link RetrieveFeaturePropertiesLiveCommandAnswerBuilderImpl}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class RetrieveFeaturePropertiesLiveCommandAnswerBuilderImplTest {

    @Mock
    private RetrieveFeaturePropertiesLiveCommand commandMock;

    private RetrieveFeaturePropertiesLiveCommandAnswerBuilderImpl underTest;

    /** */
    @Before
    public void setUp() {
        Mockito.when(commandMock.getThingId()).thenReturn(TestConstants.Thing.THING_ID);
        Mockito.when(commandMock.getDittoHeaders()).thenReturn(DittoHeaders.empty());
        Mockito.when(commandMock.getFeatureId()).thenReturn(TestConstants.Feature.FLUX_CAPACITOR_ID);

        underTest = RetrieveFeaturePropertiesLiveCommandAnswerBuilderImpl.newInstance(commandMock);
    }

    /** */
    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToGetNewInstanceWithNullCommand() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> RetrieveFeaturePropertiesLiveCommandAnswerBuilderImpl.newInstance(null))
                .withMessage(MessageFormat.format("The {0} must not be null!", "command"))
                .withNoCause();
    }

    /** */
    @Test
    public void buildAnswerWithoutResponse() {
        final LiveCommandAnswer liveCommandAnswer = underTest.withoutResponse().build();

        assertThat(liveCommandAnswer)
                .hasNoResponse()
                .hasNoEvent();
    }

    /** */
    @Test
    public void buildAnswerWithRetrieveFeaturePropertiesResponseOnly() {
        final FeatureProperties featureProperties = TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES;

        final LiveCommandAnswer liveCommandAnswer =
                underTest.withResponse(responseFactory -> responseFactory.retrieved(featureProperties))
                        .build();

        assertThat(liveCommandAnswer)
                .hasNoEvent()
                .hasThingQueryCommandResponse()
                .hasType(RetrieveFeaturePropertiesResponse.TYPE)
                .hasDittoHeaders(DittoHeaders.empty())
                .hasResourcePath(JsonPointer.of("features/" + TestConstants.Feature.FLUX_CAPACITOR_ID + "/properties"));
    }

    /** */
    @Test
    public void buildAnswerWithFeaturePropertiesNotAccessibleErrorResponseOnly() {
        final LiveCommandAnswer liveCommandAnswer =
                underTest.withResponse(
                        RetrieveFeaturePropertiesLiveCommandAnswerBuilder.ResponseFactory::featurePropertiesNotAccessibleError)
                        .build();

        assertThat(liveCommandAnswer)
                .hasNoEvent()
                .hasThingErrorResponse()
                .withType(ThingErrorResponse.TYPE)
                .withDittoHeaders(DittoHeaders.empty())
                .withStatus(HttpStatusCode.NOT_FOUND)
                .withDittoRuntimeExceptionOfType(FeaturePropertiesNotAccessibleException.class);
    }

}