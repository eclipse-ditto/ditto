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
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.TestConstants;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingTooLargeException;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.things.model.signals.commands.ThingCommandSizeValidator;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureProperties;
import org.eclipse.ditto.things.model.signals.events.FeaturePropertiesCreated;
import org.eclipse.ditto.things.model.signals.events.FeaturePropertiesModified;
import org.eclipse.ditto.things.service.persistence.actors.ETagTestUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit test for {@link ModifyFeaturePropertiesStrategy}.
 */
public final class ModifyFeaturePropertiesStrategyTest extends AbstractCommandStrategyTest {

    private static final long THING_SIZE_LIMIT_BYTES = Long.parseLong(
            System.getProperty(ThingCommandSizeValidator.DITTO_LIMITS_THINGS_MAX_SIZE_BYTES, "-1"));

    private static String featureId;
    private static FeatureProperties modifiedFeatureProperties;

    private ModifyFeaturePropertiesStrategy underTest;

    @BeforeClass
    public static void initTestFixture() {
        featureId = TestConstants.Feature.FLUX_CAPACITOR_ID;
        modifiedFeatureProperties = TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES.setValue("target_year_3", 1337);
    }

    @Before
    public void setUp() {
        underTest = new ModifyFeaturePropertiesStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyFeaturePropertiesStrategy.class, areImmutable());
    }

    @Test
    public void modifyFeaturePropertiesOfThingWithoutFeatures() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final ModifyFeatureProperties command =
                ModifyFeatureProperties.of(context.getState(), featureId, modifiedFeatureProperties,
                        DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.featureNotFound(context.getState(), command.getFeatureId(),
                        command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.removeFeatures(), command, expectedException);
    }

    @Test
    public void modifyFeaturePropertiesOfThingWithoutThatFeature() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final ModifyFeatureProperties command =
                ModifyFeatureProperties.of(context.getState(), featureId, modifiedFeatureProperties,
                        DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.featureNotFound(context.getState(), command.getFeatureId(),
                        command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.removeFeature(featureId), command, expectedException);
    }

    @Test
    public void modifyFeaturePropertiesOfFeatureWithoutProperties() {
        final Feature featureWithoutProperties = TestConstants.Feature.FLUX_CAPACITOR.removeProperties();
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final ModifyFeatureProperties command =
                ModifyFeatureProperties.of(context.getState(), featureId, modifiedFeatureProperties,
                        DittoHeaders.empty());

        assertModificationResult(underTest, THING_V2.setFeature(featureWithoutProperties), command,
                FeaturePropertiesCreated.class,
                ETagTestUtils.modifyFeaturePropertiesResponse(context.getState(), command.getFeatureId(),
                        command.getProperties(), command.getDittoHeaders(), true));
    }

    @Test
    public void modifyExistingFeatureProperties() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final ModifyFeatureProperties command =
                ModifyFeatureProperties.of(context.getState(), featureId, modifiedFeatureProperties,
                        DittoHeaders.empty());

        assertModificationResult(underTest, THING_V2, command,
                FeaturePropertiesModified.class,
                ETagTestUtils.modifyFeaturePropertiesResponse(context.getState(), command.getFeatureId(),
                        modifiedFeatureProperties, command.getDittoHeaders(), false));
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
        final ThingId thingId = ThingId.of("foo","bar");
        final Thing thing = Thing.newBuilder()
                .setId(thingId)
                .setAttributes(largeAttributes)
                .build();

        // creating the Thing should be possible as we are below the limit:
        CreateThing.of(thing, null, DittoHeaders.empty());

        final ModifyFeatureProperties command =
                ModifyFeatureProperties.of(thingId, feature.getId(), feature.getProperties().get(),
                        DittoHeaders.empty());

        // but modifying the feature properties which would cause the Thing to exceed the limit should not be allowed:
        assertThatThrownBy(() -> underTest.doApply(context, thing, NEXT_REVISION, command, null))
                .isInstanceOf(ThingTooLargeException.class);
    }

}
