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

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.function.Function;

import javax.annotation.concurrent.Immutable;

import org.eclipse.ditto.json.JsonObject;

import com.mongodb.BasicDBObject;

/**
 * This function maps a specified {@link com.mongodb.BasicDBObject} to a {@link org.eclipse.ditto.json.JsonObject}.
 * While mapping, the keys of all JSON objects can be revised by utilising a configurable function.
 */
@Immutable
final class BasicDBObjectToJsonObjectMapper extends AbstractBasicDBMapper<BasicDBObject, JsonObject> {

    private BasicDBObjectToJsonObjectMapper(final Function<String, String> theJsonKeyNameReviser) {
        super(theJsonKeyNameReviser);
    }

    /**
     * Returns an instance of {@code BasicDBObjectToJsonObjectMapper}.
     *
     * @param jsonKeyNameReviser is used to revise the json key names of JSON objects.
     * @return the instance.
     * @throws NullPointerException if {@code jsonKeyNameReviser} is {@code null}.
     */
    public static BasicDBObjectToJsonObjectMapper getInstance(final Function<String, String> jsonKeyNameReviser) {
        return new BasicDBObjectToJsonObjectMapper(jsonKeyNameReviser);
    }

    @Override
    public JsonObject apply(final BasicDBObject basicDBObject) {
        return mapBasicDBObjectToJsonObject(checkNotNull(basicDBObject, "BasicDBObject to be mapped"),
                jsonKeyNameReviser);
    }
}
