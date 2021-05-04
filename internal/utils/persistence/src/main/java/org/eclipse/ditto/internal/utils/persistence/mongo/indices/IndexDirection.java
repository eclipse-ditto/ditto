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
package org.eclipse.ditto.internal.utils.persistence.mongo.indices;

import org.bson.BsonInt32;

/**
 * Defines the direction of a {@link DefaultIndexKey}.
 */
public enum IndexDirection {
    ASCENDING(1), DESCENDING(-1);

    public static final IndexDirection DEFAULT = ASCENDING;

    private final BsonInt32 bsonInt;

    IndexDirection(final int intValue) {
        this.bsonInt = new BsonInt32(intValue);
    }

    public BsonInt32 getBsonInt() {
        return bsonInt;
    }
}
