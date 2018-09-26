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
import static org.eclipse.ditto.services.things.persistence.actors.ETagTestUtils.retrieveFeaturePropertyResponse;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
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
        final RetrieveFeaturePropertyResponse expectedResponse =
                retrieveFeaturePropertyResponse(command.getThingId(), command.getFeatureId(),
                        command.getPropertyPointer(), JsonFactory.newValue(1955), command.getDittoHeaders());

        assertQueryResult(underTest, THING_V2, command, expectedResponse);
    }

    @Test
    public void getPropertyFromThingWithoutFeatures() {
        final CommandStrategy.Context context = getDefaultContext();
        final RetrieveFeatureProperty command = RetrieveFeatureProperty.of(context.getThingId(), FLUX_CAPACITOR_ID,
                JsonFactory.newPointer("target_year_1"), DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.featureNotFound(command.getThingId(), command.getFeatureId(),
                        command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.removeFeatures(), command, expectedException);
    }

    @Test
    public void getPropertyFromFeatureWithoutProperties() {
        final CommandStrategy.Context context = getDefaultContext();
        final RetrieveFeatureProperty command =
                RetrieveFeatureProperty.of(context.getThingId(), FLUX_CAPACITOR_ID,
                        JsonFactory.newPointer("target_year_1"), DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.featurePropertiesNotFound(command.getThingId(), command.getFeatureId(),
                        command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.setFeature(FLUX_CAPACITOR.removeProperties()), command, expectedException);
    }

    @Test
    public void getNonExistentProperty() {
        final JsonPointer propertyPointer = JsonFactory.newPointer("target_year_1");
        final CommandStrategy.Context context =
                getDefaultContext();
        final RetrieveFeatureProperty command =
                RetrieveFeatureProperty.of(context.getThingId(), FLUX_CAPACITOR_ID, propertyPointer,
                        DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.featurePropertyNotFound(command.getThingId(), command.getFeatureId(),
                        command.getPropertyPointer(), command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.setFeature(FLUX_CAPACITOR.removeProperty(propertyPointer)), command,
                        expectedException);
    }

}
