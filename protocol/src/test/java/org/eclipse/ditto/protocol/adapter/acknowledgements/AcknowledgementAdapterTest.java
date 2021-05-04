/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.protocol.adapter.acknowledgements;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.base.model.acks.AcknowledgementLabel;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.protocol.Adaptable;
import org.eclipse.ditto.protocol.adapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocol.Payload;
import org.eclipse.ditto.protocol.adapter.ProtocolAdapterTest;
import org.eclipse.ditto.protocol.TestConstants;
import org.eclipse.ditto.protocol.TopicPath;
import org.eclipse.ditto.base.model.signals.acks.Acknowledgement;
import org.eclipse.ditto.things.model.signals.acks.ThingAcknowledgementFactory;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit test for {@link AcknowledgementAdapter}.
 */
public final class AcknowledgementAdapterTest implements ProtocolAdapterTest {

    private static final AcknowledgementLabel KNOWN_CUSTOM_LABEL = AcknowledgementLabel.of("my-custom-ack");

    private static TopicPath topicPathMyCustomAck;

    private AcknowledgementAdapter underTest;

    @BeforeClass
    public static void initConstants() {
        topicPathMyCustomAck = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .acks()
                .label(KNOWN_CUSTOM_LABEL)
                .build();
    }

    @Before
    public void setUp() {
        underTest = AcknowledgementAdapter.getInstance(DittoProtocolAdapter.getHeaderTranslator());
    }

    @Test
    public void acknowledgementFromAdaptable() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
        final JsonValue customAckPayload = JsonValue.of("Custom Ack payload");
        final HttpStatus status = HttpStatus.CREATED;

        final Adaptable adaptable = Adaptable.newBuilder(topicPathMyCustomAck)
                .withHeaders(dittoHeaders)
                .withPayload(Payload.newBuilder(JsonPointer.empty())
                        .withValue(customAckPayload)
                        .withStatus(status)
                        .build())
                .build();

        final Acknowledgement expected = ThingAcknowledgementFactory.newAcknowledgement(KNOWN_CUSTOM_LABEL,
                TestConstants.THING_ID,
                status,
                dittoHeaders,
                customAckPayload);

        final Acknowledgement actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void acknowledgementToAdaptable() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().randomCorrelationId().build();
        final JsonValue customAckPayload = JsonObject.newBuilder().set("foo", "bar").build();
        final HttpStatus status = HttpStatus.BAD_REQUEST;

        final Acknowledgement acknowledgement = ThingAcknowledgementFactory.newAcknowledgement(KNOWN_CUSTOM_LABEL,
                TestConstants.THING_ID,
                status,
                dittoHeaders,
                customAckPayload);

        final Adaptable expected = Adaptable.newBuilder(topicPathMyCustomAck)
                .withPayload(Payload.newBuilder(JsonPointer.empty())
                        .withValue(customAckPayload)
                        .withStatus(status)
                        .build())
                .withHeaders(dittoHeaders)
                .build();

        final Adaptable actual = underTest.toAdaptable(acknowledgement);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

}
