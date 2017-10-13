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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.live.assertions.LiveCommandAssertions;
import org.eclipse.ditto.signals.commands.live.base.LiveCommandAnswer;
import org.eclipse.ditto.signals.commands.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingsResponse;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit test for {@link RetrieveThingsLiveCommandAnswerBuilderImpl}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class RetrieveThingsLiveCommandAnswerBuilderImplTest {

    private static List<String> thingIds;

    @Mock
    private RetrieveThingsLiveCommand commandMock;

    private RetrieveThingsLiveCommandAnswerBuilderImpl underTest;

    /** */
    @BeforeClass
    public static void initThingIds() {
        thingIds = new ArrayList<>();
        thingIds.add(":foo");
        thingIds.add(":bar");
        thingIds.add(":baz");
    }

    /** */
    @Before
    public void setUp() {
        Mockito.when(commandMock.getDittoHeaders()).thenReturn(DittoHeaders.empty());
        Mockito.when(commandMock.getSelectedFields()).thenReturn(Optional.of(Mockito.mock(JsonFieldSelector.class)));

        underTest = RetrieveThingsLiveCommandAnswerBuilderImpl.newInstance(commandMock);
    }

    /** */
    @SuppressWarnings("ConstantConditions")
    @Test
    public void tryToGetNewInstanceWithNullCommand() {
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> RetrieveThingsLiveCommandAnswerBuilderImpl.newInstance(null))
                .withMessage(MessageFormat.format("The {0} must not be null!", "command"))
                .withNoCause();
    }

    /** */
    @Test
    public void buildAnswerWithoutResponse() {
        final LiveCommandAnswer liveCommandAnswer = underTest.withoutResponse().build();

        LiveCommandAssertions.assertThat(liveCommandAnswer)
                .hasNoResponse()
                .hasNoEvent();
    }

    /** */
    @Test
    public void buildAnswerWithRetrieveThingResponseOnly() {
        final Thing thing = TestConstants.Thing.THING;

        final LiveCommandAnswer liveCommandAnswer =
                underTest.withResponse(responseFactory -> responseFactory.retrieved(Collections.emptyList()))
                        .build();

        LiveCommandAssertions.assertThat(liveCommandAnswer)
                .hasNoEvent()
                .hasThingQueryCommandResponse()
                .hasType(RetrieveThingsResponse.TYPE)
                .hasDittoHeaders(DittoHeaders.empty())
                .hasResourcePath(JsonPointer.empty());
    }

}