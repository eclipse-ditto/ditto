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
package org.eclipse.ditto.protocoladapter;

import java.util.UUID;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.acks.Acknowledgement;
import org.eclipse.ditto.signals.acks.Acknowledgements;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit tests for {@link AcknowledgementAdapter}.
 */
public class AcknowledgementAdapterTest implements ProtocolAdapterTest {

    protected static final AcknowledgementLabel KNOWN_CUSTOM_LABEL = AcknowledgementLabel.of("my-custom-ack");

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
        underTest = AcknowledgementAdapter.of(DittoProtocolAdapter.getHeaderTranslator());
    }

    @Test
    public void acknowledgementFromAdaptable() {

        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .correlationId(UUID.randomUUID().toString())
                .build();
        final JsonValue customAckPayload = JsonValue.of("Custom Ack payload");
        final HttpStatusCode status = HttpStatusCode.CREATED;
        final Acknowledgement expected = Acknowledgements.newAcknowledgement(KNOWN_CUSTOM_LABEL,
                TestConstants.THING_ID,
                status,
                customAckPayload,
                dittoHeaders);

        final JsonPointer path = JsonPointer.empty();

        final Adaptable adaptable = Adaptable.newBuilder(topicPathMyCustomAck)
                .withPayload(Payload.newBuilder(path)
                        .withValue(customAckPayload)
                        .withStatus(status)
                        .build())
                .withHeaders(dittoHeaders)
                .build();
        final Acknowledgement actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void acknowledgementToAdaptable() {
        final JsonPointer path = JsonPointer.empty();
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .correlationId(UUID.randomUUID().toString())
                .build();
        final JsonValue customAckPayload = JsonObject.newBuilder().set("foo", "bar").build();
        final HttpStatusCode status = HttpStatusCode.BAD_REQUEST;
        final Adaptable expected = Adaptable.newBuilder(topicPathMyCustomAck)
                .withPayload(Payload.newBuilder(path)
                        .withValue(customAckPayload)
                        .withStatus(status)
                        .build())
                .withHeaders(dittoHeaders)
                .build();

        final Acknowledgement acknowledgement = Acknowledgements.newAcknowledgement(KNOWN_CUSTOM_LABEL,
                TestConstants.THING_ID,
                status,
                customAckPayload,
                dittoHeaders);
        final Adaptable actual = underTest.toAdaptable(acknowledgement);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }
}