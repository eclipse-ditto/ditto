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
package org.eclipse.ditto.services.thingsearch.persistence.util;

import org.bson.conversions.Bson;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;

/**
 * Checks that the keys of a MongoDB {@code $set} operation are valid.
 */
public class MongoSetKeyValidity {

    private static final String SET_OPERATOR = "$set";

    /**
     * Asserts that the operand of any {@code $set} operator in the Bson object contains no character reserved by
     * MongoDB as keys of objects in the value part.
     *
     * @param bson Bson object possibly containing the {@code $set} operator.
     */
    public static void ensure(final Bson bson) {
        ensure(JsonFactory.newObject(org.eclipse.ditto.services.utils.persistence.mongo.BsonUtil.toBsonDocument(bson).toJson()));
    }

    /**
     * Asserts that the operand of any {@code $set} operator in the Json object contains no character reserved by
     * MongoDB as keys of objects in the value part.
     *
     * @param jsonObject Json object possibly containing the {@code $set} operator.
     */
    public static void ensure(final JsonObject jsonObject) {
        jsonObject.getValue(SET_OPERATOR)
                .ifPresent(operand -> operand.asObject()
                        .forEach(jsonField -> ensureSetOperand(jsonField.getValue(), jsonObject)));
    }

    private static void ensureSetOperand(final JsonValue jsonValue, final JsonObject context) {
        if (jsonValue.isObject()) {
            jsonValue.asObject().forEach(jsonField -> {
                final String key = jsonField.getKeyName();
                if (isInvalid(key)) {
                    final String message =
                            String.format("The key '%s' is not valid inside a $set operation. Context: %s",
                                    key, context.toString());
                    throw new AssertionError(message);
                }
                ensureSetOperand(jsonField.getValue(), context);
            });
        } else if (jsonValue.isArray()) {
            jsonValue.asArray().forEach(value -> ensureSetOperand(value, context));
        }
    }

    private static boolean isInvalid(final String key) {
        // forbid '.' throughout; permit '$' only as the first character.
        return key.contains(".") || key.substring(1).contains("$");
    }
}
