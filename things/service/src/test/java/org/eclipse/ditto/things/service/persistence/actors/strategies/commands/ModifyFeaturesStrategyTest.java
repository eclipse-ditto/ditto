/*
 * Copyright (c) 2017 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.things.service.persistence.actors.strategies.commands;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.ditto.things.model.TestConstants.Thing.THING_V2;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.Features;
import org.eclipse.ditto.things.model.TestConstants;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingTooLargeException;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatures;
import org.eclipse.ditto.things.model.signals.events.FeaturesCreated;
import org.eclipse.ditto.things.model.signals.events.FeaturesModified;
import org.eclipse.ditto.things.service.persistence.actors.ETagTestUtils;
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
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final ModifyFeatures command = ModifyFeatures.of(context.getState(), modifiedFeatures, DittoHeaders.empty());

        assertModificationResult(underTest, THING_V2.removeFeatures(), command,
                FeaturesCreated.class,
                ETagTestUtils.modifyFeaturesResponse(context.getState(), modifiedFeatures, command.getDittoHeaders(), true));
    }

    @Test
    public void modifyExistingFeatures() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final ModifyFeatures command = ModifyFeatures.of(context.getState(), modifiedFeatures, DittoHeaders.empty());

        assertModificationResult(underTest, THING_V2, command,
                FeaturesModified.class,
                ETagTestUtils.modifyFeaturesResponse(context.getState(), modifiedFeatures, command.getDittoHeaders(), false));
    }

    @Test
    public void modifyFeaturePropertiesSoThatThingGetsTooLarge() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();

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
        final ThingId thingId = ThingId.of("foo", "bar");
        final Thing thing = Thing.newBuilder()
                .setId(thingId)
                .setAttributes(largeAttributes)
                .build();

        // creating the Thing should be possible as we are below the limit:
        CreateThing.of(thing, null, DittoHeaders.empty());

        final ModifyFeatures command =
                ModifyFeatures.of(thingId, Features.newBuilder().set(feature).build(), DittoHeaders.empty());

        // but modifying the features which would cause the Thing to exceed the limit should not be allowed:
        assertThatThrownBy(() -> underTest.doApply(context, thing, NEXT_REVISION, command, null))
                .isInstanceOf(ThingTooLargeException.class);
    }

}
