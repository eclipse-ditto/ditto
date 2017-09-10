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
package org.eclipse.ditto.services.utils.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Unit test for {@link ShardedMessageEnvelope}.
 */
public final class ShardedMessageEnvelopeTest {

    private static final String THING_ID = "org.eclipse.ditto.test:thingId";
    private static final Thing THING = Thing.newBuilder().setId(THING_ID).build();
    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.empty();
    private static final CreateThing CREATE_THING = CreateThing.of(THING, null, DITTO_HEADERS);

    private static final String MESSAGE_ID = THING_ID;
    private static final String TYPE = CreateThing.TYPE;
    private static final JsonObject MESSAGE = CREATE_THING.toJson(JsonSchemaVersion.V_2, FieldType.regularOrSpecial());

    private static final ShardedMessageEnvelope SHARDED_MESSAGE_ENVELOPE =
            ShardedMessageEnvelope.of(MESSAGE_ID, TYPE, MESSAGE, DITTO_HEADERS);

    private static final JsonObject SHARDED_MESSAGE_ENVELOPE_JSON = JsonObject.newBuilder() //
            .set(ShardedMessageEnvelope.JSON_ID, MESSAGE_ID) //
            .set(ShardedMessageEnvelope.JSON_TYPE, CreateThing.TYPE) //
            .set(ShardedMessageEnvelope.JSON_MESSAGE, MESSAGE) //
            .set(ShardedMessageEnvelope.JSON_DITTO_HEADERS, DITTO_HEADERS.toJson()) //
            .build();


    @Test
    public void testHashCodeAndEquals() {
        EqualsVerifier.forClass(ShardedMessageEnvelope.class) //
                .usingGetClass() //
                .withRedefinedSuperclass() //
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
