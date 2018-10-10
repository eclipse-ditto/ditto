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

import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V2;
import static org.eclipse.ditto.services.things.persistence.actors.ETagTestUtils.modifyFeaturePropertyResponse;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureProperty;
import org.eclipse.ditto.signals.events.things.FeaturePropertyCreated;
import org.eclipse.ditto.signals.events.things.FeaturePropertyModified;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit test for {@link ModifyFeaturePropertyStrategy}.
 */
public final class ModifyFeaturePropertyStrategyTest extends AbstractCommandStrategyTest {

    private static String featureId;
    private static JsonPointer propertyPointer;
    private static JsonValue newPropertyValue;

    private ModifyFeaturePropertyStrategy underTest;

    @BeforeClass
    public static void initTestFixture() {
        featureId = TestConstants.Feature.FLUX_CAPACITOR_ID;
        propertyPointer = JsonFactory.newPointer("target_year_3");
        newPropertyValue = JsonFactory.newValue("Foo!");
    }

    @Before
    public void setUp() {
        underTest = new ModifyFeaturePropertyStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyFeaturePropertyStrategy.class, areImmutable());
    }

    @Test
    public void modifyFeaturePropertyOnThingWithoutFeatures() {
        final CommandStrategy.Context context = getDefaultContext();
        final ModifyFeatureProperty command =
                ModifyFeatureProperty.of(context.getThingId(), featureId, propertyPointer, newPropertyValue,
                        DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.featureNotFound(context.getThingId(), command.getFeatureId(),
                        command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.removeFeatures(), command, expectedException);
    }

    @Test
    public void modifyFeaturePropertyOnThingWithoutThatFeature() {
        final CommandStrategy.Context context = getDefaultContext();
        final ModifyFeatureProperty command =
                ModifyFeatureProperty.of(context.getThingId(), featureId, propertyPointer, newPropertyValue,
                        DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.featureNotFound(context.getThingId(), command.getFeatureId(),
                        command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.removeFeature(featureId), command, expectedException);
    }

    @Test
    public void modifyFeaturePropertyOfFeatureWithoutProperties() {
        final CommandStrategy.Context context = getDefaultContext();
        final ModifyFeatureProperty command =
                ModifyFeatureProperty.of(context.getThingId(), featureId, propertyPointer, newPropertyValue,
                        DittoHeaders.empty());

        assertModificationResult(underTest, THING_V2.removeFeatureProperties(featureId), command,
                FeaturePropertyCreated.class,
                modifyFeaturePropertyResponse(context.getThingId(), command.getFeatureId(),
                        command.getPropertyPointer(), command.getPropertyValue(), command.getDittoHeaders(), true));
    }

    @Test
    public void modifyExistingFeatureProperty() {
        final CommandStrategy.Context context = getDefaultContext();
        final ModifyFeatureProperty command =
                ModifyFeatureProperty.of(context.getThingId(), featureId, propertyPointer, newPropertyValue,
                        DittoHeaders.empty());

        assertModificationResult(underTest, THING_V2, command,
                FeaturePropertyModified.class,
                modifyFeaturePropertyResponse(context.getThingId(), command.getFeatureId(),
                        command.getPropertyPointer(), command.getPropertyValue(), command.getDittoHeaders(), false));
    }

}
