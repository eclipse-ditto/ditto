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
import static org.eclipse.ditto.things.model.TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES;
import static org.eclipse.ditto.things.model.TestConstants.Thing.THING_V2;
import static org.eclipse.ditto.things.service.persistence.actors.ETagTestUtils.retrieveFeatureDesiredPropertiesResponse;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.things.model.FeatureProperties;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureDesiredProperties;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureDesiredPropertiesResponse;
import org.eclipse.ditto.things.service.persistence.actors.ETagTestUtils;
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
                ETagTestUtils.retrieveFeatureDesiredPropertiesResponse(command.getEntityId(), command.getFeatureId(),
                        FLUX_CAPACITOR_PROPERTIES, command.getDittoHeaders());

        assertQueryResult(underTest, THING_V2, command, expectedResponse);
    }

    @Test
    public void getPropertiesFromThingWithoutFeatures() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final RetrieveFeatureDesiredProperties command =
                RetrieveFeatureDesiredProperties.of(context.getState(), FLUX_CAPACITOR_ID, DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.featureNotFound(command.getEntityId(), command.getFeatureId(),
                        command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.removeFeatures(), command, expectedException);
    }

    @Test
    public void getNonExistingDesiredProperties() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final RetrieveFeatureDesiredProperties command =
                RetrieveFeatureDesiredProperties.of(context.getState(), FLUX_CAPACITOR_ID, DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.featureDesiredPropertiesNotFound(command.getEntityId(), command.getFeatureId(),
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
                ETagTestUtils.retrieveFeatureDesiredPropertiesResponse(command.getEntityId(), command.getFeatureId(),
                        FLUX_CAPACITOR_PROPERTIES,
                        FeatureProperties.newBuilder()
                                .set("target_year_1",
                                        FLUX_CAPACITOR_PROPERTIES.toJson(command.getImplementedSchemaVersion(),
                                                selectedFields).getValue("target_year_1").get()).build(),
                        DittoHeaders.empty());

        assertQueryResult(underTest, THING_V2, command, expectedResponse);
    }

}
