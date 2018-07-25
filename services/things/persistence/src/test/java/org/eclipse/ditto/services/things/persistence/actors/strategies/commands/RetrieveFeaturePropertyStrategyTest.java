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
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V2;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperty;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturePropertyResponse;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link RetrieveFeaturePropertyStrategy}.
 */
public final class RetrieveFeaturePropertyStrategyTest extends AbstractCommandStrategyTest {

    private RetrieveFeaturePropertyStrategy underTest;

    @Before
    public void setUp() {
        underTest = new RetrieveFeaturePropertyStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveFeaturePropertyStrategy.class, areImmutable());
    }

    @Test
    public void getProperty() {
        final CommandStrategy.Context context = getDefaultContext();
        final JsonPointer propertyPointer = JsonFactory.newPointer("target_year_1");
        final RetrieveFeatureProperty command =
                RetrieveFeatureProperty.of(context.getThingId(), FLUX_CAPACITOR_ID, propertyPointer,
                        DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V2, NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).contains(
                RetrieveFeaturePropertyResponse.of(command.getThingId(), command.getFeatureId(),
                        command.getPropertyPointer(), JsonFactory.newValue(1955), command.getDittoHeaders()));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void getPropertyFromThingWithoutFeatures() {
        final CommandStrategy.Context context = getDefaultContext();
        final RetrieveFeatureProperty command = RetrieveFeatureProperty.of(context.getThingId(), FLUX_CAPACITOR_ID,
                JsonFactory.newPointer("target_year_1"), DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V2.removeFeatures(), NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(
                ExceptionFactory.featureNotFound(command.getThingId(), command.getFeatureId(),
                        command.getDittoHeaders()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void getPropertyFromFeatureWithoutProperties() {
        final CommandStrategy.Context context = getDefaultContext();
        final RetrieveFeatureProperty command =
                RetrieveFeatureProperty.of(context.getThingId(), FLUX_CAPACITOR_ID,
                        JsonFactory.newPointer("target_year_1"), DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V2.setFeature(FLUX_CAPACITOR.removeProperties()), NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(
                ExceptionFactory.featurePropertiesNotFound(command.getThingId(), command.getFeatureId(),
                        command.getDittoHeaders()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void getNonExistentProperty() {
        final JsonPointer propertyPointer = JsonFactory.newPointer("target_year_1");
        final CommandStrategy.Context context =
                getDefaultContext();
        final RetrieveFeatureProperty command =
                RetrieveFeatureProperty.of(context.getThingId(), FLUX_CAPACITOR_ID, propertyPointer,
                        DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V2.setFeature(FLUX_CAPACITOR.removeProperty(propertyPointer)), NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(
                ExceptionFactory.featurePropertyNotFound(command.getThingId(), command.getFeatureId(),
                        command.getPropertyPointer(), command.getDittoHeaders()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

}
