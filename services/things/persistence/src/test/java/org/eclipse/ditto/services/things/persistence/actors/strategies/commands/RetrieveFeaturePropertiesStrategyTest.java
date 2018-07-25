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
import static org.eclipse.ditto.model.things.TestConstants.Feature.FLUX_CAPACITOR_ID;
import static org.eclipse.ditto.model.things.TestConstants.Feature.FLUX_CAPACITOR_PROPERTIES;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V2;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeatureProperties;
import org.eclipse.ditto.signals.commands.things.query.RetrieveFeaturePropertiesResponse;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link RetrieveFeaturePropertiesStrategy}.
 */
public final class RetrieveFeaturePropertiesStrategyTest extends AbstractCommandStrategyTest {

    private RetrieveFeaturePropertiesStrategy underTest;

    @Before
    public void setUp() {
        underTest = new RetrieveFeaturePropertiesStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveFeaturePropertiesStrategy.class, areImmutable());
    }

    @Test
    public void getProperties() {
        final CommandStrategy.Context context = getDefaultContext();
        final RetrieveFeatureProperties command =
                RetrieveFeatureProperties.of(context.getThingId(), FLUX_CAPACITOR_ID, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V2, NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).contains(
                RetrieveFeaturePropertiesResponse.of(command.getThingId(), command.getFeatureId(),
                        FLUX_CAPACITOR_PROPERTIES, command.getDittoHeaders()));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void getPropertiesFromThingWithoutFeatures() {
        final CommandStrategy.Context context = getDefaultContext();
        final RetrieveFeatureProperties command =
                RetrieveFeatureProperties.of(context.getThingId(), FLUX_CAPACITOR_ID, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V2.removeFeatures(), NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(
                ExceptionFactory.featureNotFound(command.getThingId(), command.getFeatureId(),
                        command.getDittoHeaders()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void getNonExistingProperties() {
        final CommandStrategy.Context context = getDefaultContext();
        final RetrieveFeatureProperties command =
                RetrieveFeatureProperties.of(context.getThingId(), FLUX_CAPACITOR_ID, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V2.setFeature(FLUX_CAPACITOR.removeProperties()), NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(
                ExceptionFactory.featurePropertiesNotFound(command.getThingId(), command.getFeatureId(),
                        command.getDittoHeaders()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

}
