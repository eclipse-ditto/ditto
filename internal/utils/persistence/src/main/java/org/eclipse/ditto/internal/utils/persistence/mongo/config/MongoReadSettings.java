/*
 * Copyright (c) 2026 Contributors to the Eclipse Foundation
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

import org.eclipse.ditto.internal.utils.persistence.api.config.ReadConcern;
import org.eclipse.ditto.internal.utils.persistence.api.config.ReadPreference;

/**
 * Maps Ditto's backend-neutral read preference / read concern to the MongoDB driver values.
 */
public final class MongoReadSettings {

    private MongoReadSettings() {
        throw new AssertionError();
    }

    /**
     * Returns the MongoDB driver read preference for the given Ditto read preference.
     *
     * @param readPreference the Ditto read preference.
     * @return the MongoDB driver read preference of the same name.
     * @throws NullPointerException if {@code readPreference} is {@code null}.
     */
    public static com.mongodb.ReadPreference readPreference(final ReadPreference readPreference) {
        checkNotNull(readPreference, "readPreference");
        return switch (readPreference) {
            case PRIMARY -> com.mongodb.ReadPreference.primary();
            case PRIMARY_PREFERRED -> com.mongodb.ReadPreference.primaryPreferred();
            case SECONDARY -> com.mongodb.ReadPreference.secondary();
            case SECONDARY_PREFERRED -> com.mongodb.ReadPreference.secondaryPreferred();
            case NEAREST -> com.mongodb.ReadPreference.nearest();
        };
    }

    /**
     * Returns the MongoDB driver read concern for the given Ditto read concern.
     *
     * @param readConcern the Ditto read concern.
     * @return the MongoDB driver read concern of the same name.
     * @throws NullPointerException if {@code readConcern} is {@code null}.
     */
    public static com.mongodb.ReadConcern readConcern(final ReadConcern readConcern) {
        checkNotNull(readConcern, "readConcern");
        return switch (readConcern) {
            case DEFAULT -> com.mongodb.ReadConcern.DEFAULT;
            case LOCAL -> com.mongodb.ReadConcern.LOCAL;
            case MAJORITY -> com.mongodb.ReadConcern.MAJORITY;
            case LINEARIZABLE -> com.mongodb.ReadConcern.LINEARIZABLE;
            case SNAPSHOT -> com.mongodb.ReadConcern.SNAPSHOT;
            case AVAILABLE -> com.mongodb.ReadConcern.AVAILABLE;
        };
    }

}
