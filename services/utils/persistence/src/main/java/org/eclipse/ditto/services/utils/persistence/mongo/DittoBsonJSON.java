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


import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldConverter;
import org.eclipse.ditto.json.JsonKey;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

/**
 * This is a specialized MongoDB BSON converter which additionally takes care that in JSON keys dots "." and dollar
 * signs "$" are replaced with their unicode representations in the {@link #parse(JsonValue)} function and vice versa in
 * the {@link #serialize(DBObject)} function.
 */
public final class DittoBsonJSON {

    private static final char DOLLAR_CHAR = '$';
    private static final char DOLLAR_UNICODE_CHAR = '\uFF04';
    private static final char DOT_CHAR = '.';
    private static final char DOT_UNICODE_CHAR = '\uFF0E';

    private static final Map<Character, Character> FORWARD_MAP;
    private static final Map<Character, Character> BACKWARD_MAP;

    static {
        FORWARD_MAP = new HashMap<>();
        FORWARD_MAP.put(DOT_CHAR, DOT_UNICODE_CHAR);
        FORWARD_MAP.put(DOLLAR_CHAR, DOLLAR_UNICODE_CHAR);

        BACKWARD_MAP = new HashMap<>();
        BACKWARD_MAP.put(DOT_UNICODE_CHAR, DOT_CHAR);
        BACKWARD_MAP.put(DOLLAR_UNICODE_CHAR, DOLLAR_CHAR);
    }

    private static final JsonFieldConverter MONGODB_JSONFIELD_CONVERTER_FORWARD =
            new MongoDBJsonKeyConverter(FORWARD_MAP);
    private static final JsonFieldConverter MONGODB_JSONFIELD_CONVERTER_BACKWARDS =
            new MongoDBJsonKeyConverter(BACKWARD_MAP);

    /*
     * Inhibit instantiation of this utility class.
     */
    private DittoBsonJSON() {
        // no-op
    }

    /**
     * Serializes the passed in {@link DBObject} to Json, applying replacement of "special" characters {@value
     * DOLLAR_CHAR} and {@value DOT_CHAR}.
     */
    public static JsonValue serialize(final DBObject object) {
        if (object instanceof BasicDBObject) {
            final String jsonString = ((BasicDBObject) object).toJson(JsonWriterSettings.builder()
                    .outputMode(JsonMode.RELAXED)
                    .build());
            return JsonFactory.readFrom(jsonString, MONGODB_JSONFIELD_CONVERTER_BACKWARDS).asObject();
        } else {
            throw new IllegalArgumentException("Can only serialize BasicDBObjects");
        }
    }

    /**
     * Parses the passed in {@link JsonObject} into an {@link DBObject}.
     */
    public static DBObject parse(final JsonValue jsonValue) {
        final String jsonString = jsonValue.toString(MONGODB_JSONFIELD_CONVERTER_FORWARD);
        return BasicDBObject.parse(jsonString);
    }

    /**
     * {@link JsonFieldConverter} implementation working on a passed in Map of Character->Character strategies for for
     * replacing "bad" chars which MongoDB doesn't like with other chars.
     */
    private static class MongoDBJsonKeyConverter implements JsonFieldConverter {

        private final Map<Character, Character> conversions;

        private MongoDBJsonKeyConverter(final Map<Character, Character> conversions) {

            this.conversions = Collections.unmodifiableMap(new HashMap<>(conversions));
        }

        @Nullable
        @Override
        public Function<JsonKey, JsonKey> getKeyConverter() {
            return jsonKey -> JsonKey.of(convertKey(jsonKey.toString()));
        }

        private String convertKey(final String jsonKey) {
            String convertedKey = jsonKey;
            for (final Map.Entry<Character, Character> entry : conversions.entrySet()) {
                convertedKey = convertedKey.replace(entry.getKey(), entry.getValue());
            }
            return convertedKey;
        }

        @Nullable
        @Override
        public Function<JsonValue, JsonValue> getValueConverter() {
            // no value conversion necessary:
            return null;
        }
    }

}

