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

import org.eclipse.ditto.json.JsonArray;

import com.mongodb.BasicDBList;

/**
 * This function maps a specified {@link BasicDBList} to a {@link JsonArray}.
 * While mapping, the keys of all JSON objects can be revised by utilising a configurable function.
 */
@Immutable
final class BasicDBListToJsonObjectMapper extends AbstractBasicDBMapper<BasicDBList, JsonArray> {

    private BasicDBListToJsonObjectMapper(final Function<String, String> theJsonKeyNameReviser) {
        super(theJsonKeyNameReviser);
    }

    /**
     * Returns an instance of {@code BasicDBObjectToJsonObjectMapper}.
     *
     * @param jsonKeyNameReviser is used to revise the json key names of JSON objects.
     * @return the instance.
     * @throws NullPointerException if {@code jsonKeyNameReviser} is {@code null}.
     */
    public static BasicDBListToJsonObjectMapper getInstance(final Function<String, String> jsonKeyNameReviser) {
        return new BasicDBListToJsonObjectMapper(jsonKeyNameReviser);
    }

    @Override
    public JsonArray apply(final BasicDBList basicDBList) {
        return AbstractBasicDBMapper.mapBasicDBListToJsonArray(
                checkNotNull(basicDBList, "BasicDBList to be mapped"), jsonKeyNameReviser);
    }

}
