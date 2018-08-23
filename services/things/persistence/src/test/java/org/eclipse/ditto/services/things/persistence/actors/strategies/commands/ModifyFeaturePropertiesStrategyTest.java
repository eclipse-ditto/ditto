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

import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V2;
import static org.eclipse.ditto.services.things.persistence.actors.ETagTestUtils.modifyFeaturePropertiesResponse;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureProperties;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesCreated;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesModified;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit test for {@link ModifyFeaturePropertiesStrategy}.
 */
public final class ModifyFeaturePropertiesStrategyTest extends AbstractCommandStrategyTest {

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
        final CommandStrategy.Context context = getDefaultContext();
        final ModifyFeatureProperties command =
                ModifyFeatureProperties.of(context.getThingId(), featureId, modifiedFeatureProperties,
                        DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.featureNotFound(context.getThingId(), command.getFeatureId(),
                        command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.removeFeatures(), command, expectedException);
    }

    @Test
    public void modifyFeaturePropertiesOfThingWithoutThatFeature() {
        final CommandStrategy.Context context = getDefaultContext();
        final ModifyFeatureProperties command =
                ModifyFeatureProperties.of(context.getThingId(), featureId, modifiedFeatureProperties,
                        DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.featureNotFound(context.getThingId(), command.getFeatureId(),
                        command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.removeFeature(featureId), command, expectedException);
    }

    @Test
    public void modifyFeaturePropertiesOfFeatureWithoutProperties() {
        final Feature featureWithoutProperties = TestConstants.Feature.FLUX_CAPACITOR.removeProperties();
        final CommandStrategy.Context context = getDefaultContext();
        final ModifyFeatureProperties command =
                ModifyFeatureProperties.of(context.getThingId(), featureId, modifiedFeatureProperties,
                        DittoHeaders.empty());

        assertModificationResult(underTest, THING_V2.setFeature(featureWithoutProperties), command,
                FeaturePropertiesCreated.class,
                modifyFeaturePropertiesResponse(context.getThingId(), command.getFeatureId(),
                        command.getProperties(), command.getDittoHeaders(), true));
    }

    @Test
    public void modifyExistingFeatureProperties() {
        final CommandStrategy.Context context = getDefaultContext();
        final ModifyFeatureProperties command =
                ModifyFeatureProperties.of(context.getThingId(), featureId, modifiedFeatureProperties,
                        DittoHeaders.empty());

        assertModificationResult(underTest, THING_V2, command,
                FeaturePropertiesModified.class,
                modifyFeaturePropertiesResponse(context.getThingId(), command.getFeatureId(),
                        modifiedFeatureProperties, command.getDittoHeaders(), false));
    }

}
