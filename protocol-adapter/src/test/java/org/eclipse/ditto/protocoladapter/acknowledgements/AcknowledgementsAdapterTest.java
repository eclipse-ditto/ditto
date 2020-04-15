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

import java.util.Arrays;
import java.util.Collections;

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
import org.eclipse.ditto.signals.acks.base.Acknowledgements;
import org.eclipse.ditto.signals.acks.things.ThingAcknowledgementFactory;
import org.eclipse.ditto.signals.acks.things.ThingAcknowledgementsFactory;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit test for {@link AcknowledgementsAdapter}.
 */
public final class AcknowledgementsAdapterTest implements ProtocolAdapterTest {

    private static final AcknowledgementLabel KNOWN_CUSTOM_LABEL = AcknowledgementLabel.of("success-ack");
    private static final HttpStatusCode KNOWN_STATUS = HttpStatusCode.CREATED;
    private static final DittoHeaders KNOWN_HEADERS = DittoHeaders.newBuilder()
            .correlationId("foobar")
            .build();
    private static final JsonValue KNOWN_PAYLOAD = JsonObject.newBuilder()
            .set("foo", 42)
            .build();
    private static final Acknowledgement KNOWN_ACK_SUCCESS = ThingAcknowledgementFactory.newAcknowledgement(KNOWN_CUSTOM_LABEL,
            TestConstants.THING_ID,
            KNOWN_STATUS,
            KNOWN_HEADERS,
            KNOWN_PAYLOAD);

    private static final AcknowledgementLabel KNOWN_CUSTOM_LABEL_2 = AcknowledgementLabel.of("error-ack");
    private static final HttpStatusCode KNOWN_STATUS_2 = HttpStatusCode.CONFLICT;
    private static final DittoHeaders KNOWN_HEADERS_2 = DittoHeaders.newBuilder()
            .correlationId("foobar")
            .build();
    private static final JsonValue KNOWN_PAYLOAD_2 = null;
    private static final Acknowledgement KNOWN_ACK_2_ERROR =
            ThingAcknowledgementFactory.newAcknowledgement(KNOWN_CUSTOM_LABEL_2,
                    TestConstants.THING_ID,
                    KNOWN_STATUS_2,
                    KNOWN_HEADERS_2,
                    KNOWN_PAYLOAD_2);

    private static final AcknowledgementLabel KNOWN_CUSTOM_LABEL_3 = AcknowledgementLabel.of("second-success-ack");
    private static final HttpStatusCode KNOWN_STATUS_3 = HttpStatusCode.NO_CONTENT;
    private static final DittoHeaders KNOWN_HEADERS_3 = DittoHeaders.newBuilder()
            .correlationId("foobar")
            .build();
    private static final JsonValue KNOWN_PAYLOAD_3 = JsonValue.of("Hello world!");
    private static final Acknowledgement KNOWN_ACK_3_SUCCESS = ThingAcknowledgementFactory.newAcknowledgement(
            KNOWN_CUSTOM_LABEL_3,
            TestConstants.THING_ID,
            KNOWN_STATUS_3,
            KNOWN_HEADERS_3,
            KNOWN_PAYLOAD_3);

    private static TopicPath topicPathMyCustomAck;

    private AcknowledgementsAdapter underTest;

    @BeforeClass
    public static void initConstants() {
        topicPathMyCustomAck = TopicPath.newBuilder(TestConstants.THING_ID)
                .things()
                .twin()
                .acks()
                .aggregatedAcks()
                .build();
    }

    @Before
    public void setUp() {
        underTest = AcknowledgementsAdapter.getInstance(DittoProtocolAdapter.getHeaderTranslator());
    }

    @Test
    public void acknowledgementsContaining1FromAdaptable() {

        final Adaptable adaptable = Adaptable.newBuilder(topicPathMyCustomAck)
                .withHeaders(KNOWN_HEADERS)
                .withPayload(Payload.newBuilder(JsonPointer.of(KNOWN_CUSTOM_LABEL))
                        .withValue(KNOWN_PAYLOAD)
                        .withStatus(KNOWN_STATUS)
                        .build())
                .build();

        final Acknowledgements expected = ThingAcknowledgementsFactory.newAcknowledgements(
                Collections.singletonList(KNOWN_ACK_SUCCESS), KNOWN_HEADERS);

        final Acknowledgements actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void acknowledgementsContaining1ToAdaptable() {
        final Acknowledgements acknowledgements = ThingAcknowledgementsFactory.newAcknowledgements(
                Collections.singletonList(KNOWN_ACK_2_ERROR), KNOWN_HEADERS);

        final Adaptable expected = Adaptable.newBuilder(topicPathMyCustomAck)
                .withPayload(Payload.newBuilder(JsonPointer.of(KNOWN_CUSTOM_LABEL_2))
                        .withStatus(KNOWN_STATUS_2)
                        .withValue(KNOWN_PAYLOAD_2)
                        .build()
                ).build();

        final Adaptable actual = underTest.toAdaptable(acknowledgements);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void acknowledgementsContaining2successAndErrorFromAdaptable() {

        final Adaptable adaptable = Adaptable.newBuilder(topicPathMyCustomAck)
                .withHeaders(KNOWN_HEADERS)
                .withPayload(Payload.newBuilder(JsonPointer.empty())
                        .withValue(JsonObject.newBuilder()
                                .set(KNOWN_CUSTOM_LABEL, JsonObject.newBuilder()
                                        .set("status", KNOWN_STATUS.toInt())
                                        .set("payload", KNOWN_PAYLOAD)
                                        .set("headers", KNOWN_HEADERS.toJson())
                                        .build())
                                .set(KNOWN_CUSTOM_LABEL_2, JsonObject.newBuilder()
                                        .set("status", KNOWN_STATUS_2.toInt())
                                        .set("headers", KNOWN_HEADERS_2.toJson())
                                        .build())
                                .build()
                        )
                        .withStatus(HttpStatusCode.FAILED_DEPENDENCY)
                        .build())
                .build();

        final Acknowledgements expected = ThingAcknowledgementsFactory.newAcknowledgements(
                Arrays.asList(KNOWN_ACK_SUCCESS, KNOWN_ACK_2_ERROR), KNOWN_HEADERS);

        final Acknowledgements actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void acknowledgementsContaining2successAndErrorToAdaptable() {
        final Acknowledgements acknowledgements = ThingAcknowledgementsFactory.newAcknowledgements(
                Arrays.asList(KNOWN_ACK_SUCCESS, KNOWN_ACK_2_ERROR), KNOWN_HEADERS);

        final Adaptable expected = Adaptable.newBuilder(topicPathMyCustomAck)
                .withPayload(Payload.newBuilder(JsonPointer.empty())
                        .withStatus(HttpStatusCode.FAILED_DEPENDENCY)
                        .withValue(JsonObject.newBuilder()
                                .set(KNOWN_CUSTOM_LABEL, JsonObject.newBuilder()
                                        .set("status", KNOWN_STATUS.toInt())
                                        .set("payload", KNOWN_PAYLOAD)
                                        .set("headers", KNOWN_HEADERS.toJson())
                                        .build())
                                .set(KNOWN_CUSTOM_LABEL_2, JsonObject.newBuilder()
                                        .set("status", KNOWN_STATUS_2.toInt())
                                        .set("headers", KNOWN_HEADERS_2.toJson())
                                        .build())
                                .build()
                        ).build()
                ).build();

        final Adaptable actual = underTest.toAdaptable(acknowledgements);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void acknowledgementsContaining2bothSuccessFromAdaptable() {

        final Adaptable adaptable = Adaptable.newBuilder(topicPathMyCustomAck)
                .withHeaders(KNOWN_HEADERS)
                .withPayload(Payload.newBuilder(JsonPointer.empty())
                        .withValue(JsonObject.newBuilder()
                                .set(KNOWN_CUSTOM_LABEL, JsonObject.newBuilder()
                                        .set("status", KNOWN_STATUS.toInt())
                                        .set("payload", KNOWN_PAYLOAD)
                                        .set("headers", KNOWN_HEADERS.toJson())
                                        .build())
                                .set(KNOWN_CUSTOM_LABEL_3, JsonObject.newBuilder()
                                        .set("status", KNOWN_STATUS_3.toInt())
                                        .set("payload", KNOWN_PAYLOAD_3)
                                        .set("headers", KNOWN_HEADERS_3.toJson())
                                        .build())
                                .build()
                        )
                        .withStatus(HttpStatusCode.OK)
                        .build())
                .build();

        final Acknowledgements expected = ThingAcknowledgementsFactory.newAcknowledgements(
                Arrays.asList(KNOWN_ACK_SUCCESS, KNOWN_ACK_3_SUCCESS), KNOWN_HEADERS);

        final Acknowledgements actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void acknowledgementsContaining2bothSuccessToAdaptable() {
        final Acknowledgements acknowledgements = ThingAcknowledgementsFactory.newAcknowledgements(
                Arrays.asList(KNOWN_ACK_SUCCESS, KNOWN_ACK_3_SUCCESS), KNOWN_HEADERS);

        final Adaptable expected = Adaptable.newBuilder(topicPathMyCustomAck)
                .withPayload(Payload.newBuilder(JsonPointer.empty())
                        .withStatus(HttpStatusCode.OK)
                        .withValue(JsonObject.newBuilder()
                                .set(KNOWN_CUSTOM_LABEL, JsonObject.newBuilder()
                                        .set("status", KNOWN_STATUS.toInt())
                                        .set("payload", KNOWN_PAYLOAD)
                                        .set("headers", KNOWN_HEADERS.toJson())
                                        .build())
                                .set(KNOWN_CUSTOM_LABEL_3, JsonObject.newBuilder()
                                        .set("status", KNOWN_STATUS_3.toInt())
                                        .set("payload", KNOWN_PAYLOAD_3)
                                        .set("headers", KNOWN_HEADERS_3.toJson())
                                        .build())
                                .build()
                        ).build()
                ).build();

        final Adaptable actual = underTest.toAdaptable(acknowledgements);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void acknowledgementsContaining3FromAdaptable() {

        final Adaptable adaptable = Adaptable.newBuilder(topicPathMyCustomAck)
                .withHeaders(KNOWN_HEADERS)
                .withPayload(Payload.newBuilder(JsonPointer.empty())
                        .withValue(JsonObject.newBuilder()
                                .set(KNOWN_CUSTOM_LABEL, JsonObject.newBuilder()
                                        .set("status", KNOWN_STATUS.toInt())
                                        .set("payload", KNOWN_PAYLOAD)
                                        .set("headers", KNOWN_HEADERS.toJson())
                                        .build())
                                .set(KNOWN_CUSTOM_LABEL_2, JsonObject.newBuilder()
                                        .set("status", KNOWN_STATUS_2.toInt())
                                        .set("payload", JsonValue.of(KNOWN_PAYLOAD_2))
                                        .set("headers", KNOWN_HEADERS_2.toJson())
                                        .build())
                                .set(KNOWN_CUSTOM_LABEL_3, JsonObject.newBuilder()
                                        .set("status", KNOWN_STATUS_3.toInt())
                                        .set("payload", KNOWN_PAYLOAD_3)
                                        .set("headers", KNOWN_HEADERS_3.toJson())
                                        .build())
                                .build()
                        )
                        .withStatus(HttpStatusCode.FAILED_DEPENDENCY)
                        .build())
                .build();

        final Acknowledgements expected = ThingAcknowledgementsFactory.newAcknowledgements(
                Arrays.asList(KNOWN_ACK_SUCCESS, KNOWN_ACK_2_ERROR, KNOWN_ACK_3_SUCCESS), KNOWN_HEADERS);

        final Acknowledgements actual = underTest.fromAdaptable(adaptable);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

    @Test
    public void acknowledgementsContaining3ToAdaptable() {
        final Acknowledgements acknowledgements = ThingAcknowledgementsFactory.newAcknowledgements(
                Arrays.asList(KNOWN_ACK_SUCCESS, KNOWN_ACK_2_ERROR, KNOWN_ACK_3_SUCCESS), KNOWN_HEADERS);

        final Adaptable expected = Adaptable.newBuilder(topicPathMyCustomAck)
                .withPayload(Payload.newBuilder(JsonPointer.empty())
                        .withStatus(HttpStatusCode.FAILED_DEPENDENCY)
                        .withValue(JsonObject.newBuilder()
                                .set(KNOWN_CUSTOM_LABEL, JsonObject.newBuilder()
                                        .set("status", KNOWN_STATUS.toInt())
                                        .set("payload", KNOWN_PAYLOAD)
                                        .set("headers", KNOWN_HEADERS.toJson())
                                        .build())
                                .set(KNOWN_CUSTOM_LABEL_2, JsonObject.newBuilder()
                                        .set("status", KNOWN_STATUS_2.toInt())
                                        .set("headers", KNOWN_HEADERS_2.toJson())
                                        .build())
                                .set(KNOWN_CUSTOM_LABEL_3, JsonObject.newBuilder()
                                        .set("status", KNOWN_STATUS_3.toInt())
                                        .set("payload", KNOWN_PAYLOAD_3)
                                        .set("headers", KNOWN_HEADERS_3.toJson())
                                        .build())
                                .build()
                        ).build()
                ).build();

        final Adaptable actual = underTest.toAdaptable(acknowledgements);

        assertWithExternalHeadersThat(actual).isEqualTo(expected);
    }

}