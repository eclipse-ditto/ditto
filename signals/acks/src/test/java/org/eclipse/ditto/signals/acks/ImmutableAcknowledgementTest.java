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

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.acks.AcknowledgementLabel;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.entity.id.EntityId;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.ThingId;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit tests for {@link ImmutableAcknowledgement}.
 */
public final class ImmutableAcknowledgementTest {

    private static final AcknowledgementLabel KNOWN_ACK_LABEL = AcknowledgementLabel.of("welcome-ack");
    private static final ThingId KNOWN_ENTITY_ID = ThingId.dummy();
    private static final HttpStatusCode KNOWN_STATUS_CODE = HttpStatusCode.OK;
    private static final JsonValue KNOWN_PAYLOAD = JsonObject.newBuilder().set("known", "payload").build();
    private static final DittoHeaders KNOWN_DITTO_HEADERS = DittoHeaders.newBuilder()
            .correlationId("some-correlation-id")
            .build();

    private static final ImmutableAcknowledgement KNOWN_ACK = ImmutableAcknowledgement.of(KNOWN_ACK_LABEL,
            KNOWN_ENTITY_ID,
            KNOWN_STATUS_CODE,
            KNOWN_PAYLOAD,
            KNOWN_DITTO_HEADERS);

    private static final JsonObject KNOWN_ACK_JSON = JsonObject.newBuilder()
            .set(Acknowledgement.JsonFields.LABEL, KNOWN_ACK_LABEL.toString())
            .set(Acknowledgement.JsonFields.ENTITY_ID, KNOWN_ENTITY_ID.toString())
            .set(Acknowledgement.JsonFields.STATUS_CODE, KNOWN_STATUS_CODE.toInt())
            .set(Acknowledgement.JsonFields.PAYLOAD, KNOWN_PAYLOAD)
            .set(Acknowledgement.JsonFields.DITTO_HEADERS, KNOWN_DITTO_HEADERS.toJson())
            .build();


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ImmutableAcknowledgement.class)
                .usingGetClass()
                .verify();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ImmutableAcknowledgement.class, areImmutable(),
                provided(AcknowledgementLabel.class, EntityId.class, DittoHeaders.class, JsonValue.class).isAlsoImmutable());
    }

    @Test
    public void toJsonReturnsExpected() {
        final JsonObject actual = KNOWN_ACK.toJson();
        assertThat(actual).isEqualTo(KNOWN_ACK_JSON);
    }

    @Test
    public void fromJsonReturnsExpected() {
        final ImmutableAcknowledgement actual = ImmutableAcknowledgement.fromJson(KNOWN_ACK_JSON);
        assertThat(actual).isEqualTo(KNOWN_ACK);
    }

}