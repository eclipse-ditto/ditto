/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.thingsearch.service.persistence.write.mapping;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.eclipse.ditto.json.JsonPointer;

/**
 * Package-private visitor that descends into BSON objects and BSON arrays.
 */
interface BsonValueVisitor<T> {

    T primitive(JsonPointer key, BsonValue value);

    T array(JsonPointer key, BsonArray value);

    T object(JsonPointer key, BsonDocument value);

    default T eval(final BsonValue value) {
        return eval(JsonPointer.empty(), value);
    }

    default T eval(final JsonPointer key, final BsonValue value) {
        switch (value.getBsonType()) {
            case DOCUMENT:
                return object(key, value.asDocument());
            case ARRAY:
                return array(key, value.asArray());
            default:
                return primitive(key, value);
        }
    }
}
