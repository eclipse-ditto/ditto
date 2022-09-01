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

import static org.eclipse.ditto.things.model.TestConstants.Thing.THING_V2;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.FeatureDefinition;
import org.eclipse.ditto.things.model.TestConstants;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeatureDefinition;
import org.eclipse.ditto.things.model.signals.events.FeatureDefinitionCreated;
import org.eclipse.ditto.things.model.signals.events.FeatureDefinitionModified;
import org.eclipse.ditto.things.service.persistence.actors.ETagTestUtils;
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
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final ModifyFeatureDefinition command =
                ModifyFeatureDefinition.of(context.getState(), featureId, modifiedFeatureDefinition,
                        DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.featureNotFound(context.getState(), featureId, command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.removeFeatures(), command, expectedException);
    }

    @Test
    public void modifyFeatureDefinitionOfThingWithoutThatFeature() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final ModifyFeatureDefinition command =
                ModifyFeatureDefinition.of(context.getState(), featureId, modifiedFeatureDefinition,
                        DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.featureNotFound(context.getState(), featureId, command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.removeFeature(featureId), command, expectedException);
    }

    @Test
    public void modifyFeatureDefinitionOfFeatureWithoutDefinition() {
        final Feature featureWithoutDefinition = TestConstants.Feature.FLUX_CAPACITOR.removeDefinition();
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final ModifyFeatureDefinition command =
                ModifyFeatureDefinition.of(context.getState(), featureId, modifiedFeatureDefinition,
                        DittoHeaders.empty());

        assertModificationResult(underTest, THING_V2.setFeature(featureWithoutDefinition), command,
                FeatureDefinitionCreated.class,
                ETagTestUtils.modifyFeatureDefinitionResponse(context.getState(), featureId, command.getDefinition(),
                        command.getDittoHeaders(), true));
    }

    @Test
    public void modifyExistingFeatureDefinition() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final ModifyFeatureDefinition command =
                ModifyFeatureDefinition.of(context.getState(), featureId, modifiedFeatureDefinition,
                        DittoHeaders.empty());

        assertModificationResult(underTest, THING_V2, command,
                FeatureDefinitionModified.class,
                ETagTestUtils.modifyFeatureDefinitionResponse(context.getState(), featureId, modifiedFeatureDefinition,
                        command.getDittoHeaders(), false));
    }

}
