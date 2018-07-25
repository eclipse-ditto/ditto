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
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeature;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureResponse;
import org.eclipse.ditto.signals.events.things.FeatureCreated;
import org.eclipse.ditto.signals.events.things.FeatureModified;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit test for {@link ModifyFeatureStrategy}.
 */
public final class ModifyFeatureStrategyTest extends AbstractCommandStrategyTest {

    private static Feature modifiedFeature;

    private ModifyFeatureStrategy underTest;

    @BeforeClass
    public static void initTestFixture() {
        modifiedFeature = TestConstants.Feature.FLUX_CAPACITOR.setProperty("speed/km", 300000);
    }

    @Before
    public void setUp() {
        underTest = new ModifyFeatureStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyFeatureStrategy.class, areImmutable());
    }

    @Test
    public void modifyFeatureOnThingWithoutFeatures() {
        final CommandStrategy.Context context = getDefaultContext();
        final ModifyFeature command = ModifyFeature.of(context.getThingId(), modifiedFeature, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V2.removeFeatures(), NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).containsInstanceOf(FeatureCreated.class);
        assertThat(result.getCommandResponse()).contains(
                ModifyFeatureResponse.created(context.getThingId(), command.getFeature(), command.getDittoHeaders()));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void modifyFeatureOnThingWithoutThatFeature() {
        final CommandStrategy.Context context = getDefaultContext();
        final ModifyFeature command = ModifyFeature.of(context.getThingId(), modifiedFeature, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V2.removeFeature(modifiedFeature.getId()), NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).containsInstanceOf(FeatureCreated.class);
        assertThat(result.getCommandResponse()).contains(
                ModifyFeatureResponse.created(context.getThingId(), command.getFeature(), command.getDittoHeaders()));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void modifyExistingFeature() {
        final CommandStrategy.Context context = getDefaultContext();
        final ModifyFeature command = ModifyFeature.of(context.getThingId(), modifiedFeature, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V2, NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).containsInstanceOf(FeatureModified.class);
        assertThat(result.getCommandResponse()).contains(
                ModifyFeatureResponse.modified(context.getThingId(), modifiedFeature.getId(),
                        command.getDittoHeaders()));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isFalse();
    }

}
