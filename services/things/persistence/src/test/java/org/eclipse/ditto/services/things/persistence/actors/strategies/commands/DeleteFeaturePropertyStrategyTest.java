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
import static org.eclipse.ditto.model.things.TestConstants.Feature.FLUX_CAPACITOR;
import static org.eclipse.ditto.model.things.TestConstants.Feature.FLUX_CAPACITOR_ID;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V1;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureProperty;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeaturePropertyResponse;
import org.eclipse.ditto.signals.events.things.FeaturePropertyDeleted;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Unit test for {@link DeleteFeaturePropertyStrategy}.
 */
public final class DeleteFeaturePropertyStrategyTest extends AbstractCommandStrategyTest {

    private static String featureId;
    private static JsonPointer propertyPointer;

    private DeleteFeaturePropertyStrategy underTest;

    @BeforeClass
    public static void initTestFixture() {
        featureId = FLUX_CAPACITOR_ID;
        propertyPointer = JsonFactory.newPointer("/target_year_2");
    }

    @Before
    public void setUp() {
        underTest = new DeleteFeaturePropertyStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DeleteFeaturePropertyStrategy.class, areImmutable());
    }

    @Test
    public void successfullyDeleteFeaturePropertyFromThing() {
        final CommandStrategy.Context context = getDefaultContext();
        final DeleteFeatureProperty command =
                DeleteFeatureProperty.of(context.getThingId(), featureId, propertyPointer, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V1, NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).containsInstanceOf(FeaturePropertyDeleted.class);
        assertThat(result.getCommandResponse()).contains(DeleteFeaturePropertyResponse.of(context.getThingId(),
                command.getFeatureId(), propertyPointer, command.getDittoHeaders()));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void deleteFeaturePropertyFromThingWithoutFeatures() {
        final CommandStrategy.Context context = getDefaultContext();
        final DeleteFeatureProperty command =
                DeleteFeatureProperty.of(context.getThingId(), featureId, propertyPointer, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V1.removeFeatures(), NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(
                ExceptionFactory.featureNotFound(context.getThingId(), command.getFeatureId(),
                        command.getDittoHeaders()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void deleteFeaturePropertyFromThingWithoutThatFeature() {
        final CommandStrategy.Context context = getDefaultContext();
        final DeleteFeatureProperty command =
                DeleteFeatureProperty.of(context.getThingId(), featureId, propertyPointer, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V1.removeFeature(featureId), NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(
                ExceptionFactory.featureNotFound(context.getThingId(), command.getFeatureId(),
                        command.getDittoHeaders()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void deleteFeaturePropertyFromFeatureWithoutProperties() {
        final Feature feature = FLUX_CAPACITOR.removeProperties();
        final CommandStrategy.Context context = getDefaultContext();
        final DeleteFeatureProperty command =
                DeleteFeatureProperty.of(context.getThingId(), feature.getId(), propertyPointer, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V1.setFeature(feature), NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(
                ExceptionFactory.featurePropertyNotFound(context.getThingId(), command.getFeatureId(),
                        propertyPointer, command.getDittoHeaders()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void deleteFeaturePropertyFromFeatureWithoutThatProperty() {
        final Feature feature = FLUX_CAPACITOR.removeProperty(propertyPointer);
        final CommandStrategy.Context context = getDefaultContext();
        final DeleteFeatureProperty command =
                DeleteFeatureProperty.of(context.getThingId(), feature.getId(), propertyPointer, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V1.setFeature(feature), NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(
                ExceptionFactory.featurePropertyNotFound(context.getThingId(), command.getFeatureId(),
                        propertyPointer, command.getDittoHeaders()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

}
