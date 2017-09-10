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

import java.util.Arrays;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThings;
import org.eclipse.ditto.signals.commands.things.query.ThingQueryCommand;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link ThingSearchAdapter}.
 */
public class ThingSearchAdapterTest {

    private ThingSearchAdapter underTest;

    @Before
    public void setUp() throws Exception {
        underTest = ThingSearchAdapter.newInstance();
    }

    /** */
    @Test
    public void retrieveThingsFromAdaptable() {
        final RetrieveThings expected =
                RetrieveThings.getBuilder(
                        Arrays.asList("org.eclipse.ditto.example:id1", "org.eclipse.ditto.example:id2"))
                        .dittoHeaders(TestConstants.DITTO_HEADERS_V_2)
                        .build();
        final TopicPath topicPath = TopicPath.fromNamespace("org.eclipse.ditto.example").twin().search().build();

        final JsonPointer path = JsonPointer.empty();

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)//
                .withPayload(Payload.newBuilder(path)
                        .withValue(JsonFactory.newObject()
                                .setValue("thingIds", JsonFactory.newArray()
                                        .add("org.eclipse.ditto.example:id1")
                                        .add("org.eclipse.ditto.example:id2"))).build())
                .withHeaders(TestConstants.HEADERS_V_2) //
                .build();

        final ThingQueryCommand actual = underTest.fromAdaptable(adaptable);

        assertThat(actual).isEqualTo(expected);
    }

    /** */
    @Test
    public void retrieveThingsToAdaptable() {
        final TopicPath topicPath = TopicPath.fromNamespace("org.eclipse.ditto.example").twin().search().build();
        final JsonPointer path = JsonPointer.empty();

        final Adaptable expected = Adaptable.newBuilder(topicPath) //
                .withPayload(Payload.newBuilder(path)
                        .withValue(JsonFactory.newObject()
                                .setValue("thingIds", JsonFactory.newArray()
                                        .add("org.eclipse.ditto.example:id1")
                                        .add("org.eclipse.ditto.example:id2"))).build())
                .withHeaders(TestConstants.HEADERS_V_2) //
                .build();

        final RetrieveThings retrieveThings =
                RetrieveThings.getBuilder(
                        Arrays.asList("org.eclipse.ditto.example:id1", "org.eclipse.ditto.example:id2"))
                        .dittoHeaders(TestConstants.DITTO_HEADERS_V_2)
                        .build();

        final Adaptable actual = underTest.toAdaptable(retrieveThings);

        assertThat(actual).isEqualTo(expected);
    }

    /** */
    @Test(expected = IllegalArgumentException.class)
    public void retrieveThingsToAdaptableWithDifferentNamespaces() {
        final TopicPath topicPath = TopicPath.fromNamespace("org.eclipse.ditto.example").twin().search().build();
        final JsonPointer path = JsonPointer.empty();

        final Adaptable expected = Adaptable.newBuilder(topicPath) //
                .withPayload(Payload.newBuilder(path)
                        .withValue(JsonFactory.newObject()
                                .setValue("thingIds", JsonFactory.newArray()
                                        .add("org.eclipse.ditto.example:id1")
                                        .add("org.eclipse.ditto.example:id2"))).build())
                .withHeaders(TestConstants.HEADERS_V_2) //
                .build();

        final RetrieveThings retrieveThings =
                RetrieveThings.getBuilder(
                        Arrays.asList("org.eclipse.ditto.example1:id1", "org.eclipse.ditto.example2:id2"))
                        .dittoHeaders(TestConstants.DITTO_HEADERS_V_2)
                        .build();

        underTest.toAdaptable(retrieveThings);
    }

}
