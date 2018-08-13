/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V2;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Features;
import org.eclipse.ditto.model.things.TestConstants;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatures;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeaturesResponse;
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

        final CommandStrategy.Result result = underTest.doApply(context, THING_V2.removeFeatures(), NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).containsInstanceOf(FeaturesCreated.class);
        assertThat(result.getCommandResponse()).contains(
                ModifyFeaturesResponse.created(context.getThingId(), command.getFeatures(), command.getDittoHeaders()));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void modifyExistingFeatures() {
        final CommandStrategy.Context context = getDefaultContext();
        final ModifyFeatures command = ModifyFeatures.of(context.getThingId(), modifiedFeatures, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V2, NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).containsInstanceOf(FeaturesModified.class);
        assertThat(result.getCommandResponse()).contains(
                ModifyFeaturesResponse.modified(context.getThingId(), command.getDittoHeaders()));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isFalse();
    }

}
