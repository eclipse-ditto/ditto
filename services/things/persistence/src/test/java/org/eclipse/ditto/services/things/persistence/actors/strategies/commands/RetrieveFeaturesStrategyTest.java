/*
 * Copyright (c) 2017 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * Contributors:
 *    Bosch Software Innovations GmbH - initial contribution
 */
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import static org.eclipse.ditto.model.things.TestConstants.Feature.FEATURES;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V2;
import static org.eclipse.ditto.services.things.persistence.actors.ETagTestUtils.retrieveFeaturesResponse;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatures;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturesResponse;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link RetrieveFeaturesStrategy}.
 */
public final class RetrieveFeaturesStrategyTest extends AbstractCommandStrategyTest {

    private RetrieveFeaturesStrategy underTest;

    @Before
    public void setUp() {
        underTest = new RetrieveFeaturesStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveFeaturesStrategy.class, areImmutable());
    }

    @Test
    public void retrieveFeaturesWithoutSelectedFields() {
        final CommandStrategy.Context context = getDefaultContext();
        final RetrieveFeatures command = RetrieveFeatures.of(context.getThingId(), DittoHeaders.empty());
        final RetrieveFeaturesResponse expectedResponse = retrieveFeaturesResponse(command.getThingId(), FEATURES,
                FEATURES.toJson(command.getImplementedSchemaVersion()), command.getDittoHeaders());

        assertQueryResult(underTest, THING_V2, command, expectedResponse);
    }

    @Test
    public void retrieveFeaturesWithSelectedFields() {
        final CommandStrategy.Context context = getDefaultContext();
        final JsonFieldSelector selectedFields = JsonFactory.newFieldSelector("maker");
        final RetrieveFeatures command =
                RetrieveFeatures.of(context.getThingId(), selectedFields, DittoHeaders.empty());
        final RetrieveFeaturesResponse expectedResponse = retrieveFeaturesResponse(command.getThingId(), FEATURES,
                FEATURES.toJson(command.getImplementedSchemaVersion(), selectedFields), command.getDittoHeaders());

        assertQueryResult(underTest, THING_V2, command, expectedResponse);
    }

    @Test
    public void retrieveFeaturesFromThingWithoutFeatures() {
        final CommandStrategy.Context context = getDefaultContext();
        final RetrieveFeatures command = RetrieveFeatures.of(context.getThingId(), DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.featuresNotFound(command.getThingId(), command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.removeFeatures(), command, expectedException);
    }

}
