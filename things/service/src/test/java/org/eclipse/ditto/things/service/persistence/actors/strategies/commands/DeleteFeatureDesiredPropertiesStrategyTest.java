/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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

import static org.eclipse.ditto.things.model.TestConstants.Feature.FLUX_CAPACITOR;
import static org.eclipse.ditto.things.model.TestConstants.Feature.FLUX_CAPACITOR_ID;
import static org.eclipse.ditto.things.model.TestConstants.Thing.THING_V2;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatureDesiredProperties;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatureDesiredPropertiesResponse;
import org.eclipse.ditto.things.model.signals.events.FeatureDesiredPropertiesDeleted;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link DeleteFeatureDesiredPropertiesStrategy}.
 */
public final class DeleteFeatureDesiredPropertiesStrategyTest extends AbstractCommandStrategyTest {

    private DeleteFeatureDesiredPropertiesStrategy underTest;

    @Before
    public void setUp() {
        underTest = new DeleteFeatureDesiredPropertiesStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DeleteFeatureDesiredPropertiesStrategy.class, areImmutable());
    }

    @Test
    public void successfullyDeleteFeatureDesiredPropertiesFromFeature() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final String featureId = FLUX_CAPACITOR_ID;
        final DeleteFeatureDesiredProperties command =
                DeleteFeatureDesiredProperties.of(context.getState(), featureId, DittoHeaders.empty());

        assertModificationResult(underTest, THING_V2, command,
                FeatureDesiredPropertiesDeleted.class,
                DeleteFeatureDesiredPropertiesResponse.of(context.getState(), featureId, command.getDittoHeaders()));
    }

    @Test
    public void deleteFeatureDesiredPropertiesFromThingWithoutFeatures() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final DeleteFeatureDesiredProperties command =
                DeleteFeatureDesiredProperties.of(context.getState(), FLUX_CAPACITOR_ID, DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.featureNotFound(context.getState(), command.getFeatureId(),
                        command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.removeFeatures(), command, expectedException);
    }

    @Test
    public void deleteFeatureDesiredPropertiesFromThingWithoutThatFeature() {
        final String featureId = FLUX_CAPACITOR_ID;
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final DeleteFeatureDesiredProperties command =
                DeleteFeatureDesiredProperties.of(context.getState(), featureId, DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.featureNotFound(context.getState(), command.getFeatureId(),
                        command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.removeFeature(featureId), command, expectedException);
    }

    @Test
    public void deleteFeatureDesiredPropertiesFromFeatureWithoutDesiredProperties() {
        final Feature feature = FLUX_CAPACITOR.removeDesiredProperties();
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final DeleteFeatureDesiredProperties command =
                DeleteFeatureDesiredProperties.of(context.getState(), feature.getId(), DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.featureDesiredPropertiesNotFound(context.getState(), command.getFeatureId(),
                        command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.setFeature(feature), command, expectedException);
    }

}
