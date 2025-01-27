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

import static org.eclipse.ditto.things.model.TestConstants.Feature.FLUX_CAPACITOR;
import static org.eclipse.ditto.things.model.TestConstants.Feature.FLUX_CAPACITOR_ID;
import static org.eclipse.ditto.things.model.TestConstants.Thing.THING_V2;

import org.apache.pekko.actor.ActorSystem;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatureDefinition;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteFeatureDefinitionResponse;
import org.eclipse.ditto.things.model.signals.events.FeatureDefinitionDeleted;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Unit test for {@link DeleteFeatureDefinitionStrategy}.
 */
public final class DeleteFeatureDefinitionStrategyTest extends AbstractCommandStrategyTest {

    private DeleteFeatureDefinitionStrategy underTest;

    @Before
    public void setUp() {
        final ActorSystem system = ActorSystem.create("test", ConfigFactory.load("test"));
        underTest = new DeleteFeatureDefinitionStrategy(system);
    }

    @Test
    public void successfullyDeleteFeatureDefinitionFromFeature() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final String featureId = FLUX_CAPACITOR_ID;
        final DeleteFeatureDefinition command =
                DeleteFeatureDefinition.of(context.getState(), featureId, provideHeaders(context));

        assertStagedModificationResult(underTest, THING_V2, command,
                FeatureDefinitionDeleted.class,
                DeleteFeatureDefinitionResponse.of(context.getState(), featureId, command.getDittoHeaders()));
    }

    @Test
    public void deleteFeatureDefinitionFromThingWithoutFeatures() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final DeleteFeatureDefinition command =
                DeleteFeatureDefinition.of(context.getState(), FLUX_CAPACITOR_ID, provideHeaders(context));
        final DittoRuntimeException expectedException =
                ExceptionFactory.featureNotFound(context.getState(), command.getFeatureId(),
                        command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.removeFeatures(), command, expectedException);
    }

    @Test
    public void deleteFeatureDefinitionFromThingWithoutThatFeature() {
        final String featureId = FLUX_CAPACITOR_ID;
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final DeleteFeatureDefinition command =
                DeleteFeatureDefinition.of(context.getState(), featureId, provideHeaders(context));
        final DittoRuntimeException expectedException =
                ExceptionFactory.featureNotFound(context.getState(), command.getFeatureId(),
                        command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.removeFeature(featureId), command, expectedException);
    }

    @Test
    public void deleteFeatureDefinitionFromFeatureWithoutDefinition() {
        final Feature feature = FLUX_CAPACITOR.removeDefinition();
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final DeleteFeatureDefinition command =
                DeleteFeatureDefinition.of(context.getState(), feature.getId(), provideHeaders(context));
        final DittoRuntimeException expectedException =
                ExceptionFactory.featureDefinitionNotFound(context.getState(), command.getFeatureId(),
                        command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.setFeature(feature), command, expectedException);
    }

}
