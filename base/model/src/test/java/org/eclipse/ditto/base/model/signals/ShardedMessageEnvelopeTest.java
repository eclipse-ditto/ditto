/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.base.model.signals;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.entity.id.NamespacedEntityId;
import org.eclipse.ditto.base.model.entity.type.EntityType;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ShardedMessageEnvelope}.
 */
public final class ShardedMessageEnvelopeTest {

    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.empty();

    private static final EntityId MESSAGE_ID =
            NamespacedEntityId.of(EntityType.of("thing"), "org.eclipse.ditto.test:thingId");
    private static final String TYPE = "message-type";
    private static final JsonObject MESSAGE = JsonFactory.newObjectBuilder().set("hello", "world").build();

    private static final ShardedMessageEnvelope SHARDED_MESSAGE_ENVELOPE =
            ShardedMessageEnvelope.of(MESSAGE_ID, TYPE, MESSAGE, DITTO_HEADERS);

    private static final JsonObject SHARDED_MESSAGE_ENVELOPE_JSON = JsonObject.newBuilder()
            .set(ShardedMessageEnvelope.JSON_ID_TYPE, MESSAGE_ID.getEntityType().toString())
            .set(ShardedMessageEnvelope.JSON_ID, String.valueOf(MESSAGE_ID))
            .set(ShardedMessageEnvelope.JSON_TYPE, TYPE)
            .set(ShardedMessageEnvelope.JSON_MESSAGE, MESSAGE)
            .set(ShardedMessageEnvelope.JSON_DITTO_HEADERS, DITTO_HEADERS.toJson())
            .build();


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ShardedMessageEnvelope.class)
                .usingGetClass()
                .withRedefinedSuperclass()
                .verify();
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullId() {
        ShardedMessageEnvelope.of(null, TYPE, MESSAGE, DITTO_HEADERS);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullType() {
        ShardedMessageEnvelope.of(MESSAGE_ID, null, MESSAGE, DITTO_HEADERS);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullPayload() {
        ShardedMessageEnvelope.of(MESSAGE_ID, TYPE, null, DITTO_HEADERS);
    }


    @Test(expected = NullPointerException.class)
    public void tryToCreateInstanceWithNullDittoHeaders() {
        ShardedMessageEnvelope.of(MESSAGE_ID, TYPE, MESSAGE, null);
    }


    @Test
    public void serializeMessage() {
        final JsonObject actual = SHARDED_MESSAGE_ENVELOPE.toJson();

        assertThat(actual).isEqualTo(SHARDED_MESSAGE_ENVELOPE_JSON);
    }


    @Test
    public void deserializeMessage() {
        final ShardedMessageEnvelope actual = ShardedMessageEnvelope.fromJson(SHARDED_MESSAGE_ENVELOPE_JSON);

        assertThat(actual).isEqualTo(SHARDED_MESSAGE_ENVELOPE);
    }

}
