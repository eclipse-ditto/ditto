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
import org.eclipse.ditto.things.model.*;
import org.junit.Test;

/**
 * Unit tests for {@link ThingEventToThingConverter}.
 */
public final class ThingEventToThingConverterTest {

    private static final String ATTR_KEY_COUNTER = "counter";
    private static final int KNOWN_COUNTER = 42;

    private static final String ATTR_KEY_CITY = "city";
    private static final String KNOWN_CITY = "Immenstaad am Bodensee";

    private static final JsonPointer DESIRED_PROPERTY_POINTER = JsonPointer.of("target_year_1");

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

    @Test
    public void testThingEventToThingConvertsFeatureDesiredPropertiesCreated() {
        long revision = 23L;
        FeatureDesiredPropertiesCreated desiredPropertiesCreated = FeatureDesiredPropertiesCreated.of(
                TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID,
                TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES, revision,null, DittoHeaders.empty(), null);

        Optional<Thing> thingOpt = ThingEventToThingConverter.thingEventToThing(desiredPropertiesCreated);
        assertThat(thingOpt).isPresent();
        Thing thing = thingOpt.get();
        assertThat(thing.getEntityId()).contains(TestConstants.Thing.THING_ID);
        assertThat(thing.getRevision()).contains(ThingRevision.newInstance(revision));
        FeatureProperties desiredProperties = getDesiredProperties(thing);
        assertThat(desiredProperties).isEqualTo(TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES);
    }

    private FeatureProperties getDesiredProperties(Thing thing) {
        Optional<Features> featuresOpt = thing.getFeatures();
        assertThat(featuresOpt).isPresent();
        Optional<Feature> featureOpt = featuresOpt.get().getFeature(TestConstants.Feature.FLUX_CAPACITOR_ID);
        assertThat(featureOpt).isPresent();
        Optional<FeatureProperties> desiredPropertiesOpt = featureOpt.get().getDesiredProperties();
        assertThat(desiredPropertiesOpt).isPresent();
        return desiredPropertiesOpt.get();
    }

    @Test
    public void testThingEventToThingConvertsFeatureDesiredPropertiesModified() {
        long revision = 23L;
        FeatureDesiredPropertiesModified desiredPropertiesModified = FeatureDesiredPropertiesModified.of(
                TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID,
                TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES, revision,null, DittoHeaders.empty(), null);

        Optional<Thing> thingOpt = ThingEventToThingConverter.thingEventToThing(desiredPropertiesModified);
        assertThat(thingOpt).isPresent();
        Thing thing = thingOpt.get();
        assertThat(thing.getEntityId()).contains(TestConstants.Thing.THING_ID);
        assertThat(thing.getRevision()).contains(ThingRevision.newInstance(revision));
        FeatureProperties desiredProperties = getDesiredProperties(thing);
        assertThat(desiredProperties).isEqualTo(TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES);
    }

    @Test
    public void testThingEventToThingConvertsFeatureDesiredPropertiesDeleted() {
        long revision = 23L;
        FeatureDesiredPropertiesDeleted desiredPropertiesDeleted = FeatureDesiredPropertiesDeleted.of(
                TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID, revision,null,
                DittoHeaders.empty(), null);
        Optional<Thing> thingOpt = ThingEventToThingConverter.thingEventToThing(desiredPropertiesDeleted);
        assertThat(thingOpt).isPresent();
        Thing thing = thingOpt.get();
        assertThat(thing.getEntityId()).contains(TestConstants.Thing.THING_ID);
        assertThat(thing.getRevision()).contains(ThingRevision.newInstance(revision));
    }

    @Test
    public void testThingEventToThingConvertsFeatureDesiredPropertyCreated() {
        long revision = 23L;
        JsonValue value = JsonValue.of(1955);
        FeatureDesiredPropertyCreated desiredPropertyCreated = FeatureDesiredPropertyCreated.of(
                TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID, DESIRED_PROPERTY_POINTER, value,
                revision, null, DittoHeaders.empty(), null);
        Optional<Thing> thingOpt = ThingEventToThingConverter.thingEventToThing(desiredPropertyCreated);
        assertThat(thingOpt).isPresent();
        Thing thing = thingOpt.get();
        assertThat(thing.getEntityId()).contains(TestConstants.Thing.THING_ID);
        assertThat(thing.getRevision()).contains(ThingRevision.newInstance(revision));
        FeatureProperties desiredProperties = getDesiredProperties(thing);
        assertThat(desiredProperties).isEqualTo(FeatureProperties.newBuilder().set("target_year_1", 1955).build());
    }

    @Test
    public void testThingEventToThingConvertsFeatureDesiredPropertyModified() {
        long revision = 23L;
        JsonValue value = JsonValue.of(1955);
        FeatureDesiredPropertyModified desiredPropertyModified = FeatureDesiredPropertyModified.of(
                TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID, DESIRED_PROPERTY_POINTER, value,
                revision, null, DittoHeaders.empty(), null);
        Optional<Thing> thingOpt = ThingEventToThingConverter.thingEventToThing(desiredPropertyModified);
        assertThat(thingOpt).isPresent();
        Thing thing = thingOpt.get();
        assertThat(thing.getEntityId()).contains(TestConstants.Thing.THING_ID);
        assertThat(thing.getRevision()).contains(ThingRevision.newInstance(revision));
        FeatureProperties desiredProperties = getDesiredProperties(thing);
        assertThat(desiredProperties).isEqualTo(FeatureProperties.newBuilder().set("target_year_1", 1955).build());
    }

    @Test
    public void testThingEventToThingConvertsFeatureDesiredPropertyDeleted() {
        long revision = 23L;
        FeatureDesiredPropertyDeleted desiredPropertyDeleted = FeatureDesiredPropertyDeleted.of(
                TestConstants.Thing.THING_ID, TestConstants.Feature.FLUX_CAPACITOR_ID, DESIRED_PROPERTY_POINTER, revision,
                null, DittoHeaders.empty(), null);
        Optional<Thing> thingOpt = ThingEventToThingConverter.thingEventToThing(desiredPropertyDeleted);
        assertThat(thingOpt).isPresent();
        Thing thing = thingOpt.get();
        assertThat(thing.getEntityId()).contains(TestConstants.Thing.THING_ID);
        assertThat(thing.getRevision()).contains(ThingRevision.newInstance(revision));
    }
}
