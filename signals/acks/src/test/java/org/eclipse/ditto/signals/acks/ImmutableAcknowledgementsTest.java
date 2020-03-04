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
package org.eclipse.ditto.signals.acks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.acks.DittoAcknowledgementLabel;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.ThingId;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link ImmutableAcknowledgements}.
 */
public final class ImmutableAcknowledgementsTest {

    private static final AcknowledgementLabel KNOWN_ACK_LABEL_1 = AcknowledgementLabel.of("welcome-ack");
    private static final AcknowledgementLabel KNOWN_ACK_LABEL_2 = DittoAcknowledgementLabel.PERSISTED;
    private static final ThingId KNOWN_ENTITY_ID = ThingId.dummy();
    private static final HttpStatusCode KNOWN_STATUS_CODE = HttpStatusCode.OK;
    private static final JsonValue KNOWN_PAYLOAD = JsonObject.newBuilder().set("known", "payload").build();
    private static final DittoHeaders KNOWN_DITTO_HEADERS = DittoHeaders.newBuilder()
            .correlationId("some-correlation-id")
            .build();

    private static final ImmutableAcknowledgement KNOWN_ACK_1 = ImmutableAcknowledgement.of(KNOWN_ACK_LABEL_1,
            KNOWN_ENTITY_ID,
            KNOWN_STATUS_CODE,
            KNOWN_PAYLOAD,
            KNOWN_DITTO_HEADERS);
    private static final ImmutableAcknowledgement KNOWN_ACK_2 = ImmutableAcknowledgement.of(KNOWN_ACK_LABEL_2,
            KNOWN_ENTITY_ID,
            KNOWN_STATUS_CODE,
            KNOWN_PAYLOAD,
            KNOWN_DITTO_HEADERS);
    private static final Map<AcknowledgementLabel, Acknowledgement> KNOWN_ACKS_MAP;

    static {
        KNOWN_ACKS_MAP = new HashMap<>();
        KNOWN_ACKS_MAP.put(KNOWN_ACK_1.getLabel(), KNOWN_ACK_1);
        KNOWN_ACKS_MAP.put(KNOWN_ACK_2.getLabel(), KNOWN_ACK_2);
    }

    private static final JsonObject KNOWN_ACKS_MAP_JSON = JsonObject.newBuilder()
            .set(KNOWN_ACK_1.getLabel(), KNOWN_ACK_1.toJson())
            .set(KNOWN_ACK_2.getLabel(), KNOWN_ACK_2.toJson())
            .build();

    private static final JsonObject KNOWN_ACKS_JSON = JsonObject.newBuilder()
            .set(Acknowledgements.JsonFields.ENTITY_ID, KNOWN_ENTITY_ID.toString())
            .set(Acknowledgements.JsonFields.STATUS_CODE, KNOWN_STATUS_CODE.toInt())
            .set(Acknowledgements.JsonFields.ACKNOWLEDGEMENTS, KNOWN_ACKS_MAP_JSON)
            .set(Acknowledgements.JsonFields.DITTO_HEADERS, KNOWN_DITTO_HEADERS.toJson())
            .build();

    private static final ImmutableAcknowledgements KNOWN_ACKS = ImmutableAcknowledgements.of(KNOWN_ENTITY_ID,
            KNOWN_STATUS_CODE,
            KNOWN_ACKS_MAP,
            KNOWN_DITTO_HEADERS);

    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableAcknowledgements.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableAcknowledgements.class, areImmutable(),
                provided(EntityId.class, HttpStatusCode.class, DittoHeaders.class,
                        AcknowledgementLabel.class, Acknowledgement.class).isAlsoImmutable());
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual = KNOWN_ACKS.toJson();
        assertThat(actual).isEqualTo(KNOWN_ACKS_JSON);
    }

    @Test
    public void fromJsonReturnsExpected() {
        final ImmutableAcknowledgements actual = ImmutableAcknowledgements.fromJson(KNOWN_ACKS_JSON);
        assertThat(actual).isEqualTo(KNOWN_ACKS);
    }
}