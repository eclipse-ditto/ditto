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
package org.eclipse.ditto.services.utils.persistence.mongo.indices;

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