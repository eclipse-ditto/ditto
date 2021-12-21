/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.protocol.adapter.things;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapterTest;
import org.eclipse.ditto.protocol.TestConstants;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.base.model.signals.ErrorRegistry;
import org.eclipse.ditto.base.model.signals.GlobalErrorRegistry;
import org.eclipse.ditto.things.model.signals.commands.ThingErrorResponse;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link ThingErrorResponseAdapter}.
 */
public class ThingErrorResponseAdapterTest implements ProtocolAdapterTest {

    private ThingErrorResponseAdapter underTest;
    private DittoRuntimeException dittoRuntimeException;

    @Before
    public void setUp() {
        final ErrorRegistry<DittoRuntimeException> errorRegistry = GlobalErrorRegistry.getInstance();
        underTest = ThingErrorResponseAdapter.of(DittoProtocolAdapter.getHeaderTranslator(), errorRegistry);
        dittoRuntimeException = ThingNotAccessibleException.newBuilder(TestConstants.THING_ID)
                .message("the message")
                .description("the description")
                .build();
    }

    @Test
    public void testFromAdaptable() {
        final ThingErrorResponse expected =
                ThingErrorResponse.of(TestConstants.THING_ID, dittoRuntimeException);

        final TopicPath topicPath =
                TopicPath.newBuilder(TestConstants.THING_ID).things().none().errors().build();
        final JsonPointer path = JsonPointer.empty();

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(dittoRuntimeException.toJson(FieldType.regularOrSpecial()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingErrorResponse actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void testFromAdaptableWithChannelLive() {
        final ThingErrorResponse expected =
                ThingErrorResponse.of(TestConstants.THING_ID, dittoRuntimeException);

        final TopicPath topicPath =
                TopicPath.newBuilder(TestConstants.THING_ID).things().live().errors().build();
        final JsonPointer path = JsonPointer.empty();

        final Adaptable adaptable = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(dittoRuntimeException.toJson(FieldType.regularOrSpecial()))
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();
        final ThingErrorResponse actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
        assertThat(actual.getDittoHeaders().getChannel()).hasValue("live");
    }

    @Test
    public void testToAdaptable() {
        final ThingErrorResponse errorResponse =
                ThingErrorResponse.of(TestConstants.THING_ID, dittoRuntimeException);

        final TopicPath topicPath =
                TopicPath.newBuilder(TestConstants.THING_ID).things().none().errors().build();
        final JsonPointer path = JsonPointer.empty();

        final Adaptable expected = Adaptable.newBuilder(topicPath)
                .withPayload(Payload.newBuilder(path)
                        .withValue(dittoRuntimeException.toJson(FieldType.regularOrSpecial()))
                        .withStatus(HttpStatus.NOT_FOUND)
                        .build())
                .withHeaders(TestConstants.HEADERS_V_2)
                .build();

        final Adaptable actual = underTest.toAdaptable(errorResponse, TopicPath.Channel.NONE);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }
}
