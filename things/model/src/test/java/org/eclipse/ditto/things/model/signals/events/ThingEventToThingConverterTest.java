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
package org.eclipse.ditto.things.model.signals.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Attributes;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingRevision;
import org.junit.Test;

/**
 * Unit tests for {@link ThingEventToThingConverter}.
 */
public final class ThingEventToThingConverterTest {

    private static final String ATTR_KEY_COUNTER = "counter";
    private static final int KNOWN_COUNTER = 42;

    private static final String ATTR_KEY_CITY = "city";
    private static final String KNOWN_CITY = "Immenstaad am Bodensee";

    @Test
    public void ensureMergeWithExtraFieldsMergesCorrectly() {
        final long revision = 23L;
        final String featureId = "some-feature";
        final int speedValue = 77;
        final FeaturePropertyModified featurePropertyModified =
                FeaturePropertyModified.of(TestConstants.Thing.THING_ID, featureId, JsonPointer.of("speed"),
                        JsonValue.of(speedValue), revision, null, DittoHeaders.empty(), null);
        final JsonFieldSelector extraSelector = JsonFieldSelector.newInstance("attributes");
        final JsonObject extra = JsonObject.newBuilder()
                .set("attributes", JsonObject.newBuilder()
                        .set(ATTR_KEY_COUNTER, KNOWN_COUNTER)
                        .set(ATTR_KEY_CITY, KNOWN_CITY)
                        .build()
                )
                .build();

        final Optional<Thing> thingOpt =
                ThingEventToThingConverter.mergeThingWithExtraFields(featurePropertyModified, extraSelector, extra);
        assertThat(thingOpt).isPresent();
        final Thing thing = thingOpt.get();
        assertThat(thing.getRevision()).contains(ThingRevision.newInstance(revision));
        assertThat(thing.getAttributes()).contains(Attributes.newBuilder()
                .set(ATTR_KEY_COUNTER, KNOWN_COUNTER)
                .set(ATTR_KEY_CITY, KNOWN_CITY)
                .build()
        );
        assertThat(thing.getFeatures()).isPresent();
        assertThat(thing.getFeatures().get().getFeature(featureId)).contains(Feature.newBuilder()
                .properties(FeatureProperties.newBuilder()
                        .set("speed", speedValue)
                        .build()
                )
                .withId(featureId)
                .build()
        );
    }

    @Test
    public void ensureMergeWithExtraFieldsPrioritizesEventValueBeforeExtra() {
        final long revision = 23L;
        final int modifiedCounterValue = 100;
        final AttributeModified attributeModified =
                AttributeModified.of(TestConstants.Thing.THING_ID, JsonPointer.of(ATTR_KEY_COUNTER),
                        JsonValue.of(modifiedCounterValue), revision, null, DittoHeaders.empty(), null);
        final JsonFieldSelector extraSelector = JsonFieldSelector.newInstance("attributes");
        final JsonObject extra = JsonObject.newBuilder()
                .set("attributes", JsonObject.newBuilder()
                        .set(ATTR_KEY_COUNTER, KNOWN_COUNTER)
                        .set(ATTR_KEY_CITY, KNOWN_CITY)
                        .build()
                )
                .build();

        final Optional<Thing> thingOpt =
                ThingEventToThingConverter.mergeThingWithExtraFields(attributeModified, extraSelector, extra);
        assertThat(thingOpt).isPresent();
        final Thing thing = thingOpt.get();
        assertThat(thing.getRevision()).contains(ThingRevision.newInstance(revision));
        assertThat(thing.getAttributes()).contains(Attributes.newBuilder()
                .set(ATTR_KEY_COUNTER, modifiedCounterValue)
                .set(ATTR_KEY_CITY, KNOWN_CITY)
                .build()
        );
    }

    @Test
    public void ensureThingMergedConvertsAsExpected() {
        final long revision = 23L;
        final JsonPointer mergedPointer = JsonPointer.of("/");
        final JsonObject mergedValue = JsonObject.newBuilder()
                .set("attributes", JsonObject.newBuilder()
                        .set("foo", "bar")
                        .build())
                .build();
        final ThingMerged thingMerged = ThingMerged.of(TestConstants.Thing.THING_ID, mergedPointer, mergedValue, revision,
                null, DittoHeaders.empty(), null);

        final Optional<Thing> thingOpt = ThingEventToThingConverter.thingEventToThing(thingMerged);
        assertThat(thingOpt).isPresent();
        final Thing thing = thingOpt.get();
        assertThat(thing.getEntityId()).contains(TestConstants.Thing.THING_ID);
        assertThat(thing.getRevision()).contains(ThingRevision.newInstance(revision));
        assertThat(thing.getAttributes()).contains(Attributes.newBuilder().set("foo", "bar").build());
    }
}
