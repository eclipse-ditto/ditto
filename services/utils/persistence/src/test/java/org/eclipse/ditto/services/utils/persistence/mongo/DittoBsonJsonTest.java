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
package org.eclipse.ditto.services.utils.persistence.mongo;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonValue;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

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
        final BasicDBObject parse = BasicDBObject.parse(JSON_WITH_DOTS_INKEYS);
        final JsonValue serialized = underTest.serialize(parse);

        JSONAssert.assertEquals(JSON_WITH_DOTS_INKEYS, serialized.toString(), true);
    }

    @Test
    public void serializeJsonWithUnicodeDotsInKeys() throws JSONException {
        final BasicDBObject parse = BasicDBObject.parse(JSON_WITH_UNICODE_DOTS_INKEYS);
        final JsonValue serialized = underTest.serialize(parse);

        JSONAssert.assertEquals(JSON_WITH_DOTS_INKEYS, serialized.toString(), true);
    }

    @Test
    public void serializeJsonWithDollarsInKeys() throws JSONException {
        final BasicDBObject parse = BasicDBObject.parse(JSON_WITH_DOLLAR_INKEYS);
        final JsonValue serialized = underTest.serialize(parse);

        JSONAssert.assertEquals(JSON_WITH_DOLLAR_INKEYS, serialized.toString(), true);
    }

    @Test
    public void serializeJsonWithUnicodeDollarsInKeys() throws JSONException {
        final BasicDBObject parse = BasicDBObject.parse(JSON_WITH_UNICODE_DOLLAR_INKEYS);
        final JsonValue serialized = underTest.serialize(parse);

        JSONAssert.assertEquals(JSON_WITH_DOLLAR_INKEYS, serialized.toString(), true);
    }

    @Test
    public void serializeJsonNestedWithotsInKeys() throws JSONException {
        final BasicDBObject parse = BasicDBObject.parse(JSON_NESTED_WITH_DOTS_INKEYS);
        final JsonValue serialized = underTest.serialize(parse);

        JSONAssert.assertEquals(JSON_NESTED_WITH_DOTS_INKEYS, serialized.toString(), true);
    }

    @Test
    public void serializeJsonNestedWithUnicodeDotsInKeys() throws JSONException {
        final BasicDBObject parse = BasicDBObject.parse(JSON_NESTED_WITH_UNICODE_DOTS_INKEYS);
        final JsonValue serialized = underTest.serialize(parse);

        JSONAssert.assertEquals(JSON_NESTED_WITH_DOTS_INKEYS, serialized.toString(), true);
    }

    @Test
    public void parseJsonWithDotsInKeys() throws JSONException {
        final BasicDBObject expected = BasicDBObject.parse(JSON_WITH_UNICODE_DOTS_INKEYS);
        final DBObject parsed = underTest.parse(JsonFactory.newObject(JSON_WITH_DOTS_INKEYS));

        assertThat(parsed).isEqualTo(expected);
    }

    @Test
    public void parseJsonWithUnicodeDotsInKeys() throws JSONException {
        final BasicDBObject expected = BasicDBObject.parse(JSON_WITH_UNICODE_DOTS_INKEYS);
        final DBObject parsed = underTest.parse(JsonFactory.newObject(JSON_WITH_DOTS_INKEYS));

        assertThat(parsed).isEqualTo(expected);
    }

    @Test
    public void parseJsonWithDollarsInKeys() throws JSONException {
        final BasicDBObject expected = BasicDBObject.parse(JSON_WITH_UNICODE_DOLLAR_INKEYS);
        final DBObject parsed = underTest.parse(JsonFactory.newObject(JSON_WITH_DOLLAR_INKEYS));

        assertThat(parsed).isEqualTo(expected);
    }

    @Test
    public void parseJsonWithUnicodeDollarsInKeys() throws JSONException {
        final BasicDBObject expected = BasicDBObject.parse(JSON_WITH_UNICODE_DOLLAR_INKEYS);
        final DBObject parsed = underTest.parse(JsonFactory.newObject(JSON_WITH_DOLLAR_INKEYS));

        assertThat(parsed).isEqualTo(expected);
    }

    @Test
    public void parseJsonWithNestedUnicodeDotsInKeys() throws JSONException {
        final BasicDBObject expected = BasicDBObject.parse(JSON_NESTED_WITH_UNICODE_DOTS_INKEYS);
        final DBObject parsed = underTest.parse(JsonFactory.newObject(JSON_NESTED_WITH_DOTS_INKEYS));

        assertThat(parsed).isEqualTo(expected);
    }

}
