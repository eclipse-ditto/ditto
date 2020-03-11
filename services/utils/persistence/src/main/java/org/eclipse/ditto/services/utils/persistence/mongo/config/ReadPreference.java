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
package org.eclipse.ditto.services.utils.persistence.mongo.config;

import static org.eclipse.ditto.model.base.common.ConditionChecker.checkNotNull;

import java.util.Arrays;
import java.util.Optional;

/**
 * Enumeration of read preferences for mongo client.
 */
public enum ReadPreference {

    PRIMARY("primary"),
    PRIMARY_PREFERRED("primaryPreferred"),
    SECONDARY("secondary"),
    SECONDARY_PREFERRED("secondaryPreferred"),
    NEAREST("nearest");

    private final String readPreference;

    ReadPreference(final String readPreference) {
        this.readPreference = readPreference;
    }

    /**
     * Tries to create a ReadPreference from the given read preference string.
     *
     * @param readPreference the string value of read preference.
     * @return An optional of the ReadPreference matching to the given read preference string. Empty if no matching
     * ReadPreference exists.
     */
    static Optional<ReadPreference> ofReadPreference(final String readPreference) {
        checkNotNull(readPreference, "readPreference");
        return Arrays.stream(values())
                .filter(c -> c.readPreference.contentEquals(readPreference))
                .findFirst();
    }

}
