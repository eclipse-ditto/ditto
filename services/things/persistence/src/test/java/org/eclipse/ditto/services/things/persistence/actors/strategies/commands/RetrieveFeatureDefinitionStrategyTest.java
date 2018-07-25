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

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.model.things.TestConstants.Feature.FLUX_CAPACITOR;
import static org.eclipse.ditto.model.things.TestConstants.Feature.FLUX_CAPACITOR_DEFINITION;
import static org.eclipse.ditto.model.things.TestConstants.Feature.FLUX_CAPACITOR_ID;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V2;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

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

        final CommandStrategy.Result result = underTest.doApply(context, THING_V2, NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).contains(
                RetrieveFeatureDefinitionResponse.of(command.getThingId(), command.getFeatureId(),
                        FLUX_CAPACITOR_DEFINITION, command.getDittoHeaders()));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void getDefinitionFromThingWithoutFeatures() {
        final CommandStrategy.Context context = getDefaultContext();
        final RetrieveFeatureDefinition command =
                RetrieveFeatureDefinition.of(context.getThingId(), FLUX_CAPACITOR_ID, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V2.removeFeatures(), NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(
                ExceptionFactory.featureNotFound(command.getThingId(), command.getFeatureId(),
                        command.getDittoHeaders()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void getNonExistingDefinition() {
        final CommandStrategy.Context context = getDefaultContext();
        final RetrieveFeatureDefinition command =
                RetrieveFeatureDefinition.of(context.getThingId(), FLUX_CAPACITOR_ID, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V2.setFeature(FLUX_CAPACITOR.removeDefinition()), NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(
                ExceptionFactory.featureDefinitionNotFound(command.getThingId(), command.getFeatureId(),
                        command.getDittoHeaders()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

}
