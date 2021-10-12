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
package org.eclipse.ditto.internal.utils.persistence.mongo.config;

import static org.eclipse.ditto.base.model.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.Optional;

/**
 * Enumeration of read concerns for mongo client.
 */
public enum ReadConcern {

    DEFAULT("default", com.mongodb.ReadConcern.DEFAULT),
    LOCAL("local", com.mongodb.ReadConcern.LOCAL),
    MAJORITY("majority", com.mongodb.ReadConcern.MAJORITY),
    LINEARIZABLE("linearizable", com.mongodb.ReadConcern.LINEARIZABLE),
    SNAPSHOT("snapshot", com.mongodb.ReadConcern.SNAPSHOT),
    AVAILABLE("available", com.mongodb.ReadConcern.AVAILABLE);


    private final String name;
    private final com.mongodb.ReadConcern mongoReadConcern;

    ReadConcern(final String name, final com.mongodb.ReadConcern mongoReadConcern) {
        this.name = name;
        this.mongoReadConcern = mongoReadConcern;
    }

    public com.mongodb.ReadConcern getMongoReadConcern() {
        return mongoReadConcern;
    }

    /**
     * Tries to create a ReadConcern from the given read concern string.
     *
     * @param readConcern the string value of read concern.
     * @return An optional of the ReadConcern matching to the given read preference string. Empty if no matching
     * ReadConcern exists.
     */
    public static Optional<ReadConcern> ofReadConcern(final String readConcern) {
        checkNotNull(readConcern, "readConcern");
        return Arrays.stream(values())
                .filter(c -> c.name.contentEquals(readConcern))
                .findFirst();
    }

}
