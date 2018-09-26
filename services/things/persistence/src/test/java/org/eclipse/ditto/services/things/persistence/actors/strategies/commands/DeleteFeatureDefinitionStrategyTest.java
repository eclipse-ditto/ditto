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

import static org.eclipse.ditto.model.things.TestConstants.Feature.FLUX_CAPACITOR;
import static org.eclipse.ditto.model.things.TestConstants.Feature.FLUX_CAPACITOR_ID;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V2;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureDefinition;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureDefinitionResponse;
import org.eclipse.ditto.signals.events.things.FeatureDefinitionDeleted;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link DeleteFeatureDefinitionStrategy}.
 */
public final class DeleteFeatureDefinitionStrategyTest extends AbstractCommandStrategyTest {

    private DeleteFeatureDefinitionStrategy underTest;

    @Before
    public void setUp() {
        underTest = new DeleteFeatureDefinitionStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DeleteFeatureDefinitionStrategy.class, areImmutable());
    }

    @Test
    public void successfullyDeleteFeatureDefinitionFromFeature() {
        final CommandStrategy.Context context = getDefaultContext();
        final String featureId = FLUX_CAPACITOR_ID;
        final DeleteFeatureDefinition command =
                DeleteFeatureDefinition.of(context.getThingId(), featureId, DittoHeaders.empty());

        assertModificationResult(underTest, THING_V2, command,
                FeatureDefinitionDeleted.class,
                DeleteFeatureDefinitionResponse.of(context.getThingId(), featureId, command.getDittoHeaders()));
    }

    @Test
    public void deleteFeatureDefinitionFromThingWithoutFeatures() {
        final CommandStrategy.Context context = getDefaultContext();
        final DeleteFeatureDefinition command =
                DeleteFeatureDefinition.of(context.getThingId(), FLUX_CAPACITOR_ID, DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.featureNotFound(context.getThingId(), command.getFeatureId(),
                        command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.removeFeatures(), command, expectedException);
    }

    @Test
    public void deleteFeatureDefinitionFromThingWithoutThatFeature() {
        final String featureId = FLUX_CAPACITOR_ID;
        final CommandStrategy.Context context = getDefaultContext();
        final DeleteFeatureDefinition command =
                DeleteFeatureDefinition.of(context.getThingId(), featureId, DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.featureNotFound(context.getThingId(), command.getFeatureId(),
                        command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.removeFeature(featureId), command, expectedException);
    }

    @Test
    public void deleteFeatureDefinitionFromFeatureWithoutDefinition() {
        final Feature feature = FLUX_CAPACITOR.removeDefinition();
        final CommandStrategy.Context context = getDefaultContext();
        final DeleteFeatureDefinition command =
                DeleteFeatureDefinition.of(context.getThingId(), feature.getId(), DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.featureDefinitionNotFound(context.getThingId(), command.getFeatureId(),
                        command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.setFeature(feature), command, expectedException);
    }

}
