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

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.TestConstants;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeatureProperty;
import org.eclipse.ditto.signals.commands.things.modify.ModifyFeaturePropertyResponse;
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

        final CommandStrategy.Result result = underTest.doApply(context, THING_V2.removeFeatures(), NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(
                ExceptionFactory.featureNotFound(context.getThingId(), command.getFeatureId(),
                        command.getDittoHeaders()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void modifyFeaturePropertyOnThingWithoutThatFeature() {
        final CommandStrategy.Context context = getDefaultContext();
        final ModifyFeatureProperty command =
                ModifyFeatureProperty.of(context.getThingId(), featureId, propertyPointer, newPropertyValue,
                        DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V2.removeFeature(featureId), NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(
                ExceptionFactory.featureNotFound(context.getThingId(), command.getFeatureId(),
                        command.getDittoHeaders()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void modifyFeaturePropertyOfFeatureWithoutProperties() {
        final CommandStrategy.Context context = getDefaultContext();
        final ModifyFeatureProperty command =
                ModifyFeatureProperty.of(context.getThingId(), featureId, propertyPointer, newPropertyValue,
                        DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V2.removeFeatureProperties(featureId), NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).containsInstanceOf(FeaturePropertyCreated.class);
        assertThat(result.getCommandResponse()).contains(
                ModifyFeaturePropertyResponse.created(context.getThingId(), command.getFeatureId(),
                        command.getPropertyPointer(), command.getPropertyValue(), command.getDittoHeaders()));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void modifyExistingFeatureProperty() {
        final CommandStrategy.Context context = getDefaultContext();
        final ModifyFeatureProperty command =
                ModifyFeatureProperty.of(context.getThingId(), featureId, propertyPointer, newPropertyValue,
                        DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V2, NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).containsInstanceOf(FeaturePropertyModified.class);
        assertThat(result.getCommandResponse()).contains(
                ModifyFeaturePropertyResponse.modified(context.getThingId(), command.getFeatureId(),
                        command.getPropertyPointer(), command.getDittoHeaders()));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isFalse();
    }

}
