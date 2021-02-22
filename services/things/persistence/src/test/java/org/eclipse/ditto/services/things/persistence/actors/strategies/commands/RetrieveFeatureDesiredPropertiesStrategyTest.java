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
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import static org.eclipse.ditto.model.things.TestConstants.Feature.FLUX_CAPACITOR;
import static org.eclipse.ditto.model.things.TestConstants.Feature.FLUX_CAPACITOR_ID;
import static org.eclipse.ditto.model.things.TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V2;
import static org.eclipse.ditto.services.things.persistence.actors.ETagTestUtils.retrieveFeatureDesiredPropertiesResponse;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.FeatureProperties;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureDesiredProperties;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureDesiredPropertiesResponse;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link RetrieveFeatureDesiredPropertiesStrategy}.
 */
public final class RetrieveFeatureDesiredPropertiesStrategyTest extends AbstractCommandStrategyTest {

    private RetrieveFeatureDesiredPropertiesStrategy underTest;

    @Before
    public void setUp() {
        underTest = new RetrieveFeatureDesiredPropertiesStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveFeatureDesiredPropertiesStrategy.class, areImmutable());
    }

    @Test
    public void getProperties() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final RetrieveFeatureDesiredProperties command =
                RetrieveFeatureDesiredProperties.of(context.getState(), FLUX_CAPACITOR_ID, DittoHeaders.empty());
        final RetrieveFeatureDesiredPropertiesResponse expectedResponse =
                retrieveFeatureDesiredPropertiesResponse(command.getThingEntityId(), command.getFeatureId(),
                        FLUX_CAPACITOR_PROPERTIES, command.getDittoHeaders());

        assertQueryResult(underTest, THING_V2, command, expectedResponse);
    }

    @Test
    public void getPropertiesFromThingWithoutFeatures() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final RetrieveFeatureDesiredProperties command =
                RetrieveFeatureDesiredProperties.of(context.getState(), FLUX_CAPACITOR_ID, DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.featureNotFound(command.getThingEntityId(), command.getFeatureId(),
                        command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.removeFeatures(), command, expectedException);
    }

    @Test
    public void getNonExistingDesiredProperties() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final RetrieveFeatureDesiredProperties command =
                RetrieveFeatureDesiredProperties.of(context.getState(), FLUX_CAPACITOR_ID, DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.featureDesiredPropertiesNotFound(command.getThingEntityId(), command.getFeatureId(),
                        command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.setFeature(FLUX_CAPACITOR.removeDesiredProperties()), command,
                expectedException);
    }

    @Test
    public void retrievePropertiesWithSelectedFields() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final JsonFieldSelector selectedFields = JsonFactory.newFieldSelector("target_year_1");
        final RetrieveFeatureDesiredProperties command =
                RetrieveFeatureDesiredProperties.of(context.getState(), FLUX_CAPACITOR_ID, selectedFields,
                        DittoHeaders.empty());
        final RetrieveFeatureDesiredPropertiesResponse expectedResponse =
                retrieveFeatureDesiredPropertiesResponse(command.getThingEntityId(), command.getFeatureId(),
                        FLUX_CAPACITOR_PROPERTIES,
                        FeatureProperties.newBuilder()
                                .set("target_year_1",
                                        FLUX_CAPACITOR_PROPERTIES.toJson(command.getImplementedSchemaVersion(),
                                                selectedFields).getValue("target_year_1").get()).build(),
                        DittoHeaders.empty());

        assertQueryResult(underTest, THING_V2, command, expectedResponse);
    }

}
