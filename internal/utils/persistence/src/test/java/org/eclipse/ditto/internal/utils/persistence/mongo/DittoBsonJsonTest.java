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
package org.eclipse.ditto.internal.utils.persistence.mongo;

import static org.assertj.core.api.Assertions.assertThat;

import org.bson.BsonDocument;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonValue;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;

/**
 * Unit test for {@link DittoBsonJson}.
 */
public final class DittoBsonJsonTest {

    private static final String JSON_WITH_DOTS_INKEYS = "{" +
            "\"org.eclipse.ditto\": 42" +
            "}";

    private static final String JSON_WITH_UNICODE_DOTS_INKEYS = "{" +
            "\"org．eclipse．ditto\": 42" +
            "}";

    private static final String JSON_WITH_DOLLAR_INKEYS = "{" +
            "\"$something\": 42" +
            "}";

    private static final String JSON_WITH_UNICODE_DOLLAR_INKEYS = "{" +
            "\"＄something\": 42" +
            "}";

    private static final String JSON_NESTED_WITH_DOTS_INKEYS = "{ \"foo\": {" +
            "\"org.eclipse.ditto\": 42" +
            "}}";

    private static final String JSON_NESTED_WITH_UNICODE_DOTS_INKEYS = "{ \"foo\": {" +
            "\"org．eclipse．ditto\": 42" +
            "}}";

    private DittoBsonJson underTest;

    @Before
    public void setUp() {
        underTest = DittoBsonJson.getInstance();
    }

    @Test
    public void serializeJsonWithDotsInKeys() throws JSONException {
        final BsonDocument parse = BsonDocument.parse(JSON_WITH_DOTS_INKEYS);
        final JsonValue serialized = underTest.serialize(parse);

        JSONAssert.assertEquals(JSON_WITH_DOTS_INKEYS, serialized.toString(), true);
    }

    @Test
    public void serializeJsonWithUnicodeDotsInKeys() throws JSONException {
        final BsonDocument parse = BsonDocument.parse(JSON_WITH_UNICODE_DOTS_INKEYS);
        final JsonValue serialized = underTest.serialize(parse);

        JSONAssert.assertEquals(JSON_WITH_DOTS_INKEYS, serialized.toString(), true);
    }

    @Test
    public void serializeJsonWithDollarsInKeys() throws JSONException {
        final BsonDocument parse = BsonDocument.parse(JSON_WITH_DOLLAR_INKEYS);
        final JsonValue serialized = underTest.serialize(parse);

        JSONAssert.assertEquals(JSON_WITH_DOLLAR_INKEYS, serialized.toString(), true);
    }

    @Test
    public void serializeJsonWithUnicodeDollarsInKeys() throws JSONException {
        final BsonDocument parse = BsonDocument.parse(JSON_WITH_UNICODE_DOLLAR_INKEYS);
        final JsonValue serialized = underTest.serialize(parse);

        JSONAssert.assertEquals(JSON_WITH_DOLLAR_INKEYS, serialized.toString(), true);
    }

    @Test
    public void serializeJsonNestedWithotsInKeys() throws JSONException {
        final BsonDocument parse = BsonDocument.parse(JSON_NESTED_WITH_DOTS_INKEYS);
        final JsonValue serialized = underTest.serialize(parse);

        JSONAssert.assertEquals(JSON_NESTED_WITH_DOTS_INKEYS, serialized.toString(), true);
    }

    @Test
    public void serializeJsonNestedWithUnicodeDotsInKeys() throws JSONException {
        final BsonDocument parse = BsonDocument.parse(JSON_NESTED_WITH_UNICODE_DOTS_INKEYS);
        final JsonValue serialized = underTest.serialize(parse);

        JSONAssert.assertEquals(JSON_NESTED_WITH_DOTS_INKEYS, serialized.toString(), true);
    }

    @Test
    public void parseJsonWithDotsInKeys() throws JSONException {
        final BsonDocument expected = BsonDocument.parse(JSON_WITH_UNICODE_DOTS_INKEYS);
        final Object parsed = underTest.parse(JsonFactory.newObject(JSON_WITH_DOTS_INKEYS));

        assertThat(parsed).isEqualTo(expected);
    }

    @Test
    public void parseJsonWithUnicodeDotsInKeys() throws JSONException {
        final BsonDocument expected = BsonDocument.parse(JSON_WITH_UNICODE_DOTS_INKEYS);
        final Object parsed = underTest.parse(JsonFactory.newObject(JSON_WITH_DOTS_INKEYS));

        assertThat(parsed).isEqualTo(expected);
    }

    @Test
    public void parseJsonWithDollarsInKeys() throws JSONException {
        final BsonDocument expected = BsonDocument.parse(JSON_WITH_UNICODE_DOLLAR_INKEYS);
        final Object parsed = underTest.parse(JsonFactory.newObject(JSON_WITH_DOLLAR_INKEYS));

        assertThat(parsed).isEqualTo(expected);
    }

    @Test
    public void parseJsonWithUnicodeDollarsInKeys() throws JSONException {
        final BsonDocument expected = BsonDocument.parse(JSON_WITH_UNICODE_DOLLAR_INKEYS);
        final Object parsed = underTest.parse(JsonFactory.newObject(JSON_WITH_DOLLAR_INKEYS));

        assertThat(parsed).isEqualTo(expected);
    }

    @Test
    public void parseJsonWithNestedUnicodeDotsInKeys() throws JSONException {
        final BsonDocument expected = BsonDocument.parse(JSON_NESTED_WITH_UNICODE_DOTS_INKEYS);
        final Object parsed = underTest.parse(JsonFactory.newObject(JSON_NESTED_WITH_DOTS_INKEYS));

        assertThat(parsed).isEqualTo(expected);
    }

}
