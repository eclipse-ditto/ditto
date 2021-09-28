/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
 * Enumeration of read preferences for mongo client.
 */
public enum ReadPreference {

    PRIMARY("primary", com.mongodb.ReadPreference.primary()),
    PRIMARY_PREFERRED("primaryPreferred", com.mongodb.ReadPreference.primaryPreferred()),
    SECONDARY("secondary", com.mongodb.ReadPreference.secondary()),
    SECONDARY_PREFERRED("secondaryPreferred", com.mongodb.ReadPreference.secondaryPreferred()),
    NEAREST("nearest", com.mongodb.ReadPreference.nearest());

    private final String name;
    private final com.mongodb.ReadPreference mongoReadPreference;

    ReadPreference(final String name, final com.mongodb.ReadPreference mongoReadPreference) {
        this.name = name;
        this.mongoReadPreference = mongoReadPreference;
    }

    public com.mongodb.ReadPreference getMongoReadPreference() {
        return mongoReadPreference;
    }

    /**
     * Tries to create a ReadPreference from the given read preference string.
     *
     * @param readPreference the string value of read preference.
     * @return An optional of the ReadPreference matching to the given read preference string. Empty if no matching
     * ReadPreference exists.
     */
    public static Optional<ReadPreference> ofReadPreference(final String readPreference) {
        checkNotNull(readPreference, "readPreference");
        return Arrays.stream(values())
                .filter(c -> c.name.contentEquals(readPreference))
                .findFirst();
    }

}
