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
package org.eclipse.ditto.things.service.timeseries;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.events.ThingMerged;
import org.junit.Test;

public final class ThingEventLeafExtractorTest {

    private static final ThingId THING_ID = ThingId.of("org.eclipse.ditto", "sensor-1");

    @Test
    public void nullInMergePatchIsNoDataPoint() {
        final ThingMerged event = ThingMerged.of(THING_ID, JsonPointer.of("/features/env/properties"),
                JsonObject.newBuilder()
                        .set("temperature", JsonValue.nullLiteral())
                        .set("humidity", 42)
                        .build(),
                1L, Instant.parse("2026-01-01T00:00:00Z"), DittoHeaders.empty(), null);

        assertThat(ThingEventLeafExtractor.extractLeaves(event))
                .containsExactly(new ThingEventLeafExtractor.PropertyLeaf("env", JsonPointer.of("/humidity"),
                        JsonValue.of(42)));
    }

}
