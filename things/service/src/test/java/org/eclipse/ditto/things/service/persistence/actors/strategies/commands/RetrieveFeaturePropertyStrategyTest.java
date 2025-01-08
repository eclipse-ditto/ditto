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
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureProperty;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeaturePropertyResponse;
import org.eclipse.ditto.things.service.persistence.actors.ETagTestUtils;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

/**
 * Unit test for {@link RetrieveFeaturePropertyStrategy}.
 */
public final class RetrieveFeaturePropertyStrategyTest extends AbstractCommandStrategyTest {

    private RetrieveFeaturePropertyStrategy underTest;

    @Before
    public void setUp() {
        final ActorSystem system = ActorSystem.create("test", ConfigFactory.load("test"));
        underTest = new RetrieveFeaturePropertyStrategy(system);
    }

    @Test
    public void getProperty() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final JsonPointer propertyPointer = JsonFactory.newPointer("target_year_1");
        final RetrieveFeatureProperty command =
                RetrieveFeatureProperty.of(context.getState(), FLUX_CAPACITOR_ID, propertyPointer,
                        provideHeaders(context));
        final RetrieveFeaturePropertyResponse expectedResponse =
                ETagTestUtils.retrieveFeaturePropertyResponse(command.getEntityId(), command.getFeatureId(),
                        command.getPropertyPointer(), JsonFactory.newValue(1955), command.getDittoHeaders());

        assertQueryResult(underTest, THING_V2, command, expectedResponse);
    }

    @Test
    public void getPropertyFromThingWithoutFeatures() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final RetrieveFeatureProperty command = RetrieveFeatureProperty.of(context.getState(), FLUX_CAPACITOR_ID,
                JsonFactory.newPointer("target_year_1"), provideHeaders(context));
        final DittoRuntimeException expectedException =
                ExceptionFactory.featureNotFound(command.getEntityId(), command.getFeatureId(),
                        command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.removeFeatures(), command, expectedException);
    }

    @Test
    public void getPropertyFromFeatureWithoutProperties() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final RetrieveFeatureProperty command =
                RetrieveFeatureProperty.of(context.getState(), FLUX_CAPACITOR_ID,
                        JsonFactory.newPointer("target_year_1"), provideHeaders(context));
        final DittoRuntimeException expectedException =
                ExceptionFactory.featurePropertiesNotFound(command.getEntityId(), command.getFeatureId(),
                        command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.setFeature(FLUX_CAPACITOR.removeProperties()), command,
                expectedException);
    }

    @Test
    public void getNonExistentProperty() {
        final JsonPointer propertyPointer = JsonFactory.newPointer("target_year_1");
        final CommandStrategy.Context<ThingId> context =
                getDefaultContext();
        final RetrieveFeatureProperty command =
                RetrieveFeatureProperty.of(context.getState(), FLUX_CAPACITOR_ID, propertyPointer,
                        provideHeaders(context));
        final DittoRuntimeException expectedException =
                ExceptionFactory.featurePropertyNotFound(command.getEntityId(), command.getFeatureId(),
                        command.getPropertyPointer(), command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.setFeature(FLUX_CAPACITOR.removeProperty(propertyPointer)), command,
                expectedException);
    }

}
