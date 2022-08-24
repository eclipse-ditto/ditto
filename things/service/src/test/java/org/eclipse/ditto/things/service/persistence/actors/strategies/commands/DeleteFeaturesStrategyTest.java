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
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatures;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeaturesResponse;
import org.eclipse.ditto.things.model.signals.events.FeaturesDeleted;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link DeleteFeaturesStrategy}.
 */
public final class DeleteFeaturesStrategyTest extends AbstractCommandStrategyTest {

    private DeleteFeaturesStrategy underTest;

    @Before
    public void setUp() {
        underTest = new DeleteFeaturesStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DeleteFeaturesStrategy.class, areImmutable());
    }

    @Test
    public void successfullyDeleteFeaturesFromThing() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final DeleteFeatures command = DeleteFeatures.of(context.getState(), DittoHeaders.empty());

        assertModificationResult(underTest, THING_V2, command,
                FeaturesDeleted.class,
                DeleteFeaturesResponse.of(context.getState(), command.getDittoHeaders()));
    }

    @Test
    public void deleteFeaturesFromThingWithoutFeatures() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final DeleteFeatures command = DeleteFeatures.of(context.getState(), DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.featuresNotFound(context.getState(), command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.removeFeatures(), command, expectedException);
    }

}
