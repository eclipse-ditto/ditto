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
package org.eclipse.ditto.protocoladapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingsResponse;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommandResponse;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link ThingQueryCommandResponseAdapter}.
 */
public class ThingSearchResponseAdapterTest {

    private ThingSearchResponseAdapter underTest;

    @Before
    public void setUp() throws Exception {
        underTest = ThingSearchResponseAdapter.newInstance();
    }

    /** */
    @Test
    public void retrieveThingsResponseFromAdaptable() {
        final RetrieveThingsResponse expected = RetrieveThingsResponse.of(JsonFactory.newArray() //
                        .add(TestConstants.THING.toJsonString()) //
                        .add(TestConstants.THING2.toJsonString()), //
                TestConstants.DITTO_HEADERS_V_2);

        final TopicPath topicPath = TopicPath.fromNamespace(TestConstants.NAMESPACE) //
                .things() //
                .twin() //
                .search() //
                .build();

        final JsonPointer path = JsonPointer.empty();
        final Adaptable adaptable = Adaptable.newBuilder(topicPath) //
                .withPayload(Payload.newBuilder(path).withValue(JsonFactory.newArray() //
                        .add(TestConstants.THING.toJsonString()) //
                        .add(TestConstants.THING2.toJsonString())).build()) //
                .withHeaders(TestConstants.HEADERS_V_2).build();

        final ThingQueryCommandResponse actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

}
