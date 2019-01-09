/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.model.connectivity;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonObject;

public class TestConstants {

    static final Instant INSTANT = Instant.now();
    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);
    private static final Duration ONE_HOUR = Duration.ofHours(1);
    private static final Duration ONE_DAY = Duration.ofDays(1);
    static final JsonObject MEASUREMENTS = JsonFactory.newObjectBuilder()
            .set(ONE_MINUTE.toString(), ONE_MINUTE.toMillis())
            .set(ONE_HOUR.toString(), ONE_HOUR.toMillis())
            .set(ONE_DAY.toString(), ONE_DAY.toMillis())
            .set(Measurement.JsonFields.LAST_MESSAGE_AT, INSTANT.toString())
            .build();

    public static final JsonObject INBOUND_SUCCESS_JSON = getMeasurementJson("inbound", true);
    public static final JsonObject INBOUND_FAILURE_JSON = getMeasurementJson("inbound", false);
    public static final JsonObject MAPPED_SUCCESS_JSON = getMeasurementJson("mapped", true);
    public static final JsonObject MAPPED_FAILURE_JSON = getMeasurementJson("mapped", false);

    static JsonObject getMeasurementJson(final String type, final boolean success) {
        return JsonObject
                .newBuilder().set(type,
                        JsonFactory.newObjectBuilder().set(success ? "success" : "failure",
                                MEASUREMENTS
                        ).build()).build();
    }

    static final Map<Duration, Long> COUNTERS;
    static {
        COUNTERS = new HashMap<>();
        COUNTERS.put(ONE_MINUTE, ONE_MINUTE.toMillis());
        COUNTERS.put(ONE_HOUR, ONE_HOUR.toMillis());
        COUNTERS.put(ONE_DAY, ONE_DAY.toMillis());
    }

    private static Measurement getMeasurement(final String type, final boolean success) {
        return new ImmutableMeasurement(type, success, COUNTERS, INSTANT);

    }

    private static final Measurement INBOUND_SUCCESS = getMeasurement("inbound", true);
    private static final Measurement INBOUND_FAILURE = getMeasurement("inbound", false);
    private static final Measurement MAPPED_SUCCESS = getMeasurement("mapped", true);
    private static final Measurement MAPPED_FAILURE = getMeasurement("mapped", false);

    static final Set<Measurement> INBOUND_MEASUREMENTS = new HashSet<>(Arrays.asList(INBOUND_SUCCESS,
            INBOUND_FAILURE,
            MAPPED_SUCCESS,
            MAPPED_FAILURE));

}
