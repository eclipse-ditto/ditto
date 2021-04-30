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

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureDesiredProperty;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatureDesiredPropertyResponse;
import org.eclipse.ditto.things.service.persistence.actors.ETagTestUtils;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link RetrieveFeatureDesiredPropertyStrategy}.
 */
public final class RetrieveFeatureDesiredPropertyStrategyTest extends AbstractCommandStrategyTest {

    private RetrieveFeatureDesiredPropertyStrategy underTest;

    @Before
    public void setUp() {
        underTest = new RetrieveFeatureDesiredPropertyStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveFeatureDesiredPropertyStrategy.class, areImmutable());
    }

    @Test
    public void getProperty() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final JsonPointer propertyPointer = JsonFactory.newPointer("target_year_1");
        final RetrieveFeatureDesiredProperty command =
                RetrieveFeatureDesiredProperty.of(context.getState(), FLUX_CAPACITOR_ID, propertyPointer,
                        DittoHeaders.empty());
        final RetrieveFeatureDesiredPropertyResponse expectedResponse =
                ETagTestUtils.retrieveFeatureDesiredPropertyResponse(command.getEntityId(), command.getFeatureId(),
                        command.getDesiredPropertyPointer(), JsonFactory.newValue(1955), command.getDittoHeaders());

        assertQueryResult(underTest, THING_V2, command, expectedResponse);
    }

    @Test
    public void getPropertyFromThingWithoutFeatures() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final RetrieveFeatureDesiredProperty command = RetrieveFeatureDesiredProperty.of(context.getState(), FLUX_CAPACITOR_ID,
                JsonFactory.newPointer("target_year_1"), DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.featureNotFound(command.getEntityId(), command.getFeatureId(),
                        command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.removeFeatures(), command, expectedException);
    }

    @Test
    public void getPropertyFromFeatureWithoutProperties() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final RetrieveFeatureDesiredProperty command =
                RetrieveFeatureDesiredProperty.of(context.getState(), FLUX_CAPACITOR_ID,
                        JsonFactory.newPointer("target_year_1"), DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.featureDesiredPropertiesNotFound(command.getEntityId(), command.getFeatureId(),
                        command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.setFeature(FLUX_CAPACITOR.removeDesiredProperties()), command,
                expectedException);
    }

    @Test
    public void getNonExistentProperty() {
        final JsonPointer propertyPointer = JsonFactory.newPointer("target_year_1");
        final CommandStrategy.Context<ThingId> context =
                getDefaultContext();
        final RetrieveFeatureDesiredProperty command =
                RetrieveFeatureDesiredProperty.of(context.getState(), FLUX_CAPACITOR_ID, propertyPointer,
                        DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.featureDesiredPropertyNotFound(command.getEntityId(), command.getFeatureId(),
                        command.getDesiredPropertyPointer(), command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.setFeature(FLUX_CAPACITOR.removeDesiredProperty(propertyPointer)), command,
                expectedException);
    }

}
