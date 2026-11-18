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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.EnumMap;
import java.util.Map;

import org.eclipse.ditto.internal.utils.persistence.api.config.ReadConcern;
import org.eclipse.ditto.internal.utils.persistence.api.config.ReadPreference;
import org.junit.Test;

/**
 * Unit test for {@link MongoReadSettings}.
 */
public final class MongoReadSettingsTest {

    private static final Map<ReadPreference, com.mongodb.ReadPreference> EXPECTED_READ_PREFERENCES =
            new EnumMap<>(ReadPreference.class);
    private static final Map<ReadConcern, com.mongodb.ReadConcern> EXPECTED_READ_CONCERNS =
            new EnumMap<>(ReadConcern.class);

    static {
        EXPECTED_READ_PREFERENCES.put(ReadPreference.PRIMARY, com.mongodb.ReadPreference.primary());
        EXPECTED_READ_PREFERENCES.put(ReadPreference.PRIMARY_PREFERRED, com.mongodb.ReadPreference.primaryPreferred());
        EXPECTED_READ_PREFERENCES.put(ReadPreference.SECONDARY, com.mongodb.ReadPreference.secondary());
        EXPECTED_READ_PREFERENCES.put(ReadPreference.SECONDARY_PREFERRED,
                com.mongodb.ReadPreference.secondaryPreferred());
        EXPECTED_READ_PREFERENCES.put(ReadPreference.NEAREST, com.mongodb.ReadPreference.nearest());

        EXPECTED_READ_CONCERNS.put(ReadConcern.DEFAULT, com.mongodb.ReadConcern.DEFAULT);
        EXPECTED_READ_CONCERNS.put(ReadConcern.LOCAL, com.mongodb.ReadConcern.LOCAL);
        EXPECTED_READ_CONCERNS.put(ReadConcern.MAJORITY, com.mongodb.ReadConcern.MAJORITY);
        EXPECTED_READ_CONCERNS.put(ReadConcern.LINEARIZABLE, com.mongodb.ReadConcern.LINEARIZABLE);
        EXPECTED_READ_CONCERNS.put(ReadConcern.SNAPSHOT, com.mongodb.ReadConcern.SNAPSHOT);
        EXPECTED_READ_CONCERNS.put(ReadConcern.AVAILABLE, com.mongodb.ReadConcern.AVAILABLE);
    }

    @Test
    public void readPreferenceMapsEveryConstantToSameNamedDriverValue() {
        assertThat(EXPECTED_READ_PREFERENCES.keySet()).containsExactly(ReadPreference.values());
        EXPECTED_READ_PREFERENCES.forEach((readPreference, expected) ->
                assertThat(MongoReadSettings.readPreference(readPreference))
                        .as(readPreference.name())
                        .isEqualTo(expected));
    }

    @Test
    public void readConcernMapsEveryConstantToSameNamedDriverValue() {
        assertThat(EXPECTED_READ_CONCERNS.keySet()).containsExactly(ReadConcern.values());
        EXPECTED_READ_CONCERNS.forEach((readConcern, expected) ->
                assertThat(MongoReadSettings.readConcern(readConcern))
                        .as(readConcern.name())
                        .isEqualTo(expected));
    }

    @Test
    public void readPreferenceNameMatchesDriverName() {
        for (final ReadPreference readPreference : ReadPreference.values()) {
            assertThat(MongoReadSettings.readPreference(readPreference).getName())
                    .as(readPreference.name())
                    .isEqualTo(readPreference.getName());
        }
    }

    @Test
    public void readConcernLevelMatchesName() {
        for (final ReadConcern readConcern : ReadConcern.values()) {
            final com.mongodb.ReadConcern driverReadConcern = MongoReadSettings.readConcern(readConcern);
            if (readConcern == ReadConcern.DEFAULT) {
                assertThat(driverReadConcern.isServerDefault()).as(readConcern.name()).isTrue();
            } else {
                assertThat(driverReadConcern.getLevel())
                        .as(readConcern.name())
                        .isNotNull()
                        .extracting(com.mongodb.ReadConcernLevel::getValue)
                        .isEqualTo(readConcern.getName());
            }
        }
    }

    @Test
    public void nullArgumentIsRejected() {
        assertThatNullPointerException().isThrownBy(() -> MongoReadSettings.readPreference(null));
        assertThatNullPointerException().isThrownBy(() -> MongoReadSettings.readConcern(null));
    }

}
