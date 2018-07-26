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
import org.eclipse.ditto.model.things.FeatureDefinition;
import org.eclipse.ditto.model.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureDefinition;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureDefinitionResponse;
import org.eclipse.ditto.signals.events.things.FeatureDefinitionCreated;
import org.eclipse.ditto.signals.events.things.FeatureDefinitionModified;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit test for {@link ModifyFeatureDefinitionStrategy}.
 */
public final class ModifyFeatureDefinitionStrategyTest extends AbstractCommandStrategyTest {

    private static String featureId;
    private static FeatureDefinition modifiedFeatureDefinition;

    private ModifyFeatureDefinitionStrategy underTest;

    @BeforeClass
    public static void initTestFixture() {
        featureId = TestConstants.Feature.FLUX_CAPACITOR_ID;
        modifiedFeatureDefinition = FeatureDefinition.fromIdentifier("org.example:my-feature:23.42.1337");
    }

    @Before
    public void setUp() {
        underTest = new ModifyFeatureDefinitionStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyFeatureDefinitionStrategy.class, areImmutable());
    }

    @Test
    public void modifyFeatureDefinitionOfThingWithoutFeatures() {
        final CommandStrategy.Context context = getDefaultContext();
        final ModifyFeatureDefinition command =
                ModifyFeatureDefinition.of(context.getThingId(), featureId, modifiedFeatureDefinition,
                        DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V2.removeFeatures(), NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(
                ExceptionFactory.featureNotFound(context.getThingId(), featureId, command.getDittoHeaders()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void modifyFeatureDefinitionOfThingWithoutThatFeature() {
        final CommandStrategy.Context context = getDefaultContext();
        final ModifyFeatureDefinition command =
                ModifyFeatureDefinition.of(context.getThingId(), featureId, modifiedFeatureDefinition,
                        DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V2.removeFeature(featureId), NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(
                ExceptionFactory.featureNotFound(context.getThingId(), featureId, command.getDittoHeaders()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void modifyFeatureDefinitionOfFeatureWithoutDefinition() {
        final Feature featureWithoutDefinition = TestConstants.Feature.FLUX_CAPACITOR.removeDefinition();
        final CommandStrategy.Context context = getDefaultContext();
        final ModifyFeatureDefinition command =
                ModifyFeatureDefinition.of(context.getThingId(), featureId, modifiedFeatureDefinition,
                        DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V2.setFeature(featureWithoutDefinition), NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).containsInstanceOf(FeatureDefinitionCreated.class);
        assertThat(result.getCommandResponse()).contains(
                ModifyFeatureDefinitionResponse.created(context.getThingId(), featureId, command.getDefinition(),
                        command.getDittoHeaders()));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void modifyExistingFeatureDefinition() {
        final CommandStrategy.Context context = getDefaultContext();
        final ModifyFeatureDefinition command =
                ModifyFeatureDefinition.of(context.getThingId(), featureId, modifiedFeatureDefinition,
                        DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V2, NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).containsInstanceOf(FeatureDefinitionModified.class);
        assertThat(result.getCommandResponse()).contains(
                ModifyFeatureDefinitionResponse.modified(context.getThingId(), featureId, command.getDittoHeaders()));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isFalse();
    }

}
