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
package org.eclipse.ditto.protocoladapter.acknowledgements;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.protocoladapter.Adaptable;
import org.eclipse.ditto.protocoladapter.DittoProtocolAdapter;
import org.eclipse.ditto.protocoladapter.Payload;
import org.eclipse.ditto.protocoladapter.ProtocolAdapterTest;
import org.eclipse.ditto.protocoladapter.TestConstants;
import org.eclipse.ditto.protocoladapter.TopicPath;
import org.eclipse.ditto.signals.acks.base.Acknowledgement;
import org.eclipse.ditto.signals.acks.things.ThingAcknowledgementFactory;
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
        final HttpStatusCode status = HttpStatusCode.CREATED;

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
        final HttpStatusCode status = HttpStatusCode.BAD_REQUEST;

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