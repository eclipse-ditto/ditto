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
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V2;
import static org.eclipse.ditto.services.things.persistence.actors.ETagTestUtils.modifyFeaturesResponse;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.TestConstants;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingTooLargeException;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatures;
import org.eclipse.ditto.signals.events.things.FeaturesCreated;
import org.eclipse.ditto.signals.events.things.FeaturesModified;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit test for {@link ModifyFeaturesStrategy}.
 */
public final class ModifyFeaturesStrategyTest extends AbstractCommandStrategyTest {

    private static Features modifiedFeatures;

    private ModifyFeaturesStrategy underTest;

    @BeforeClass
    public static void initTestFixture() {
        modifiedFeatures = TestConstants.Feature.FEATURES.toBuilder()
                .set(ThingsModelFactory.newFeature("myNewFeature", ThingsModelFactory.newFeaturePropertiesBuilder()
                        .set("/foo/bar", "baz")
                        .build()))
                .build();
    }

    @Before
    public void setUp() {
        underTest = new ModifyFeaturesStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyFeaturesStrategy.class, areImmutable());
    }

    @Test
    public void modifyFeaturesOfThingWithoutFeatures() {
        final CommandStrategy.Context context = getDefaultContext();
        final ModifyFeatures command = ModifyFeatures.of(context.getThingId(), modifiedFeatures, DittoHeaders.empty());

        assertModificationResult(underTest, THING_V2.removeFeatures(), command,
                FeaturesCreated.class,
                modifyFeaturesResponse(context.getThingId(), modifiedFeatures, command.getDittoHeaders(), true));
    }

    @Test
    public void modifyExistingFeatures() {
        final CommandStrategy.Context context = getDefaultContext();
        final ModifyFeatures command = ModifyFeatures.of(context.getThingId(), modifiedFeatures, DittoHeaders.empty());

        assertModificationResult(underTest, THING_V2, command,
                FeaturesModified.class,
                modifyFeaturesResponse(context.getThingId(), modifiedFeatures, command.getDittoHeaders(), false));
    }

    @Test
    public void modifyFeaturePropertiesSoThatThingGetsTooLarge() {
        final CommandStrategy.Context context = getDefaultContext();

        final Feature feature = Feature.newBuilder()
                .properties(JsonObject.newBuilder().set("foo", false).set("bar", 42).build())
                .withId("myFeature")
                .build();

        final long staticOverhead = 80;
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < THING_SIZE_LIMIT_BYTES - staticOverhead; i++) {
            sb.append('a');
        }
        final JsonObject largeAttributes = JsonObject.newBuilder()
                .set("a", sb.toString())
                .build();
        final String thingId = "foo:bar";
        final Thing thing = Thing.newBuilder()
                .setId(thingId)
                .setAttributes(largeAttributes)
                .build();

        // creating the Thing should be possible as we are below the limit:
        CreateThing.of(thing, null, DittoHeaders.empty());

        final ModifyFeatures command =
                ModifyFeatures.of(thingId, Features.newBuilder().set(feature).build(), DittoHeaders.empty());

        // but modifying the features which would cause the Thing to exceed the limit should not be allowed:
        assertThatThrownBy(() -> underTest.doApply(context, thing, NEXT_REVISION, command))
                .isInstanceOf(ThingTooLargeException.class);
    }

}
