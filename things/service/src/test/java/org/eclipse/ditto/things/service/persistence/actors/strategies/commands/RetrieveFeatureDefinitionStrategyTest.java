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
import static org.eclipse.ditto.things.model.TestConstants.Feature.FLUX_CAPACITOR_DEFINITION;
import static org.eclipse.ditto.things.model.TestConstants.Feature.FLUX_CAPACITOR_ID;
import static org.eclipse.ditto.things.model.TestConstants.Thing.THING_V2;

import org.apache.pekko.actor.ActorSystem;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaderDefinition;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureDefinition;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureDefinitionResponse;
import org.eclipse.ditto.things.service.persistence.actors.ETagTestUtils;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Unit test for {@link RetrieveFeatureDefinitionStrategy}.
 */
public final class RetrieveFeatureDefinitionStrategyTest extends AbstractCommandStrategyTest {

    private RetrieveFeatureDefinitionStrategy underTest;

    @Before
    public void setUp() {
        final ActorSystem system = ActorSystem.create("test", ConfigFactory.load("test"));
        underTest = new RetrieveFeatureDefinitionStrategy(system);
    }

    @Test
    public void getDefinition() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final RetrieveFeatureDefinition command =
                RetrieveFeatureDefinition.of(context.getState(), FLUX_CAPACITOR_ID, provideHeaders(context));
        final RetrieveFeatureDefinitionResponse expectedResponse =
                ETagTestUtils.retrieveFeatureDefinitionResponse(command.getEntityId(), command.getFeatureId(),
                        FLUX_CAPACITOR_DEFINITION, command.getDittoHeaders().toBuilder()
                                .putHeader(DittoHeaderDefinition.ENTITY_REVISION.getKey(), "41")
                                .build());

        assertQueryResult(underTest, THING_V2, command, expectedResponse);
    }

    @Test
    public void getDefinitionFromThingWithoutFeatures() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final RetrieveFeatureDefinition command =
                RetrieveFeatureDefinition.of(context.getState(), FLUX_CAPACITOR_ID, provideHeaders(context));
        final DittoRuntimeException expectedException =
                ExceptionFactory.featureNotFound(command.getEntityId(), command.getFeatureId(),
                        command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.removeFeatures(), command, expectedException);
    }

    @Test
    public void getNonExistingDefinition() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final RetrieveFeatureDefinition command =
                RetrieveFeatureDefinition.of(context.getState(), FLUX_CAPACITOR_ID, provideHeaders(context));
        final DittoRuntimeException expectedException =
                ExceptionFactory.featureDefinitionNotFound(command.getEntityId(), command.getFeatureId(),
                        command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.setFeature(FLUX_CAPACITOR.removeDefinition()), command,
                expectedException);
    }

}
