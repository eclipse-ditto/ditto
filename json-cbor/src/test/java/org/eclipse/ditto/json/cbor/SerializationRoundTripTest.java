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
package org.eclipse.ditto.json.cbor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.eclipse.ditto.json.CborFactory;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.junit.Before;
import org.junit.Test;

public final class SerializationRoundTripTest {

    private CborFactory cborFactory;

    @Before
    public void setup() {
        cborFactory = new JacksonCborFactory();
    }

    @Test
    public void object2cbor2object2cbor2object() throws IOException {
        final JsonObject innerObject = JsonFactory.newObject("{\"innerkey\":\"innervalue\"}");
        final JsonObject testObject = JsonFactory.newObject()
                .setValue("key", "value")
                .setValue("integer", 123456789)
                .setValue("floating", 7.903f)
                .setValue("object", innerObject)
                .setValue("array", JsonFactory.newArray("[4,89,3.0,\"test\"]").add(innerObject));

        final ByteBuffer byteBuffer = cborFactory.toByteBuffer(testObject);
        final JsonValue jsonValue = cborFactory.readFrom(byteBuffer);
        assertThat(jsonValue).isEqualTo(testObject);

        final ByteBuffer byteBuffer1 = cborFactory.toByteBuffer(jsonValue);
        final JsonValue jsonValue1 = cborFactory.readFrom(byteBuffer1);

        assertThat(byteBuffer).isEqualTo(byteBuffer1);
        assertThat(jsonValue1).isEqualTo(testObject);
    }

    @Test
    public void object2json2object2cbor2object() throws IOException {
        final JsonObject innerObject = JsonFactory.newObject("{\"innerkey\":\"innervalue\"}");
        final JsonObject testObject = JsonFactory.newObject()
                .setValue("key", "value")
                .setValue("integer", 123456789)
                .setValue("floating", 7.903f)
                .setValue("object", innerObject)
                .setValue("array", JsonFactory.newArray("[4,89,3.0,\"test\"]").add(innerObject));

        final JsonValue jsonValue = JsonFactory.newObject(testObject.toString());
        assertThat(jsonValue).isEqualTo(testObject);

        final ByteBuffer byteBuffer1 = cborFactory.toByteBuffer(jsonValue);
        final JsonValue jsonValue1 = cborFactory.readFrom(byteBuffer1);

        assertThat(jsonValue1).isEqualTo(testObject);
    }

    @Test
    public void object2json2object2json2object() {
        final JsonObject innerObject = JsonFactory.newObject("{\"innerkey\":\"innervalue\"}");
        final JsonObject testObject = JsonFactory.newObject()
                .setValue("key", "value")
                .setValue("integer", 123456789)
                .setValue("floating", 7.903f)
                .setValue("object", innerObject)
                .setValue("array", JsonFactory.newArray("[4,89,3.0,\"test\"]").add(innerObject));

        final String string = testObject.toString();
        final JsonValue jsonValue = JsonFactory.newObject(string);
        assertThat(jsonValue).isEqualTo(testObject);

        final String string1 = jsonValue.toString();
        final JsonValue jsonValue1 = JsonFactory.newObject(string1);

        assertThat(string).isEqualTo(string1);
        assertThat(jsonValue1).isEqualTo(testObject);
    }
}
