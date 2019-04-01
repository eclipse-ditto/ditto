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
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import static org.eclipse.ditto.model.things.TestConstants.Feature.FLUX_CAPACITOR;
import static org.eclipse.ditto.model.things.TestConstants.Feature.FLUX_CAPACITOR_DEFINITION;
import static org.eclipse.ditto.model.things.TestConstants.Feature.FLUX_CAPACITOR_ID;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V2;
import static org.eclipse.ditto.services.things.persistence.actors.ETagTestUtils.retrieveFeatureDefinitionResponse;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureDefinition;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureDefinitionResponse;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link RetrieveFeatureDefinitionStrategy}.
 */
public final class RetrieveFeatureDefinitionStrategyTest extends AbstractCommandStrategyTest {

    private RetrieveFeatureDefinitionStrategy underTest;

    @Before
    public void setUp() {
        underTest = new RetrieveFeatureDefinitionStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveFeatureDefinitionStrategy.class, areImmutable());
    }

    @Test
    public void getDefinition() {
        final CommandStrategy.Context context = getDefaultContext();
        final RetrieveFeatureDefinition command =
                RetrieveFeatureDefinition.of(context.getThingId(), FLUX_CAPACITOR_ID, DittoHeaders.empty());
        final RetrieveFeatureDefinitionResponse expectedResponse =
                retrieveFeatureDefinitionResponse(command.getThingId(), command.getFeatureId(),
                        FLUX_CAPACITOR_DEFINITION, command.getDittoHeaders());

        assertQueryResult(underTest, THING_V2, command, expectedResponse);
    }

    @Test
    public void getDefinitionFromThingWithoutFeatures() {
        final CommandStrategy.Context context = getDefaultContext();
        final RetrieveFeatureDefinition command =
                RetrieveFeatureDefinition.of(context.getThingId(), FLUX_CAPACITOR_ID, DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.featureNotFound(command.getThingId(), command.getFeatureId(),
                        command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.removeFeatures(), command, expectedException);
    }

    @Test
    public void getNonExistingDefinition() {
        final CommandStrategy.Context context = getDefaultContext();
        final RetrieveFeatureDefinition command =
                RetrieveFeatureDefinition.of(context.getThingId(), FLUX_CAPACITOR_ID, DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.featureDefinitionNotFound(command.getThingId(), command.getFeatureId(),
                        command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.setFeature(FLUX_CAPACITOR.removeDefinition()), command, expectedException);
    }

}
