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
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V1;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureProperties;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeaturePropertiesResponse;
import org.eclipse.ditto.signals.events.things.FeaturePropertiesDeleted;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link DeleteFeaturePropertiesStrategy}.
 */
public final class DeleteFeaturePropertiesStrategyTest extends AbstractCommandStrategyTest {

    private DeleteFeaturePropertiesStrategy underTest;

    @Before
    public void setUp() {
        underTest = new DeleteFeaturePropertiesStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DeleteFeaturePropertiesStrategy.class, areImmutable());
    }

    @Test
    public void successfullyDeleteFeaturePropertiesFromFeature() {
        final CommandStrategy.Context context = getDefaultContext();
        final String featureId = FLUX_CAPACITOR_ID;
        final DeleteFeatureProperties command =
                DeleteFeatureProperties.of(context.getThingId(), featureId, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V1, NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).containsInstanceOf(FeaturePropertiesDeleted.class);
        assertThat(result.getCommandResponse()).contains(
                DeleteFeaturePropertiesResponse.of(context.getThingId(), featureId, command.getDittoHeaders()));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void deleteFeaturePropertiesFromThingWithoutFeatures() {
        final CommandStrategy.Context context = getDefaultContext();
        final DeleteFeatureProperties command =
                DeleteFeatureProperties.of(context.getThingId(), FLUX_CAPACITOR_ID, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V1.removeFeatures(), NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(
                ExceptionFactory.featureNotFound(context.getThingId(), command.getFeatureId(),
                        command.getDittoHeaders()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void deleteFeaturePropertiesFromThingWithoutThatFeature() {
        final String featureId = FLUX_CAPACITOR_ID;
        final CommandStrategy.Context context = getDefaultContext();
        final DeleteFeatureProperties command =
                DeleteFeatureProperties.of(context.getThingId(), featureId, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V1.removeFeature(featureId), NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(
                ExceptionFactory.featureNotFound(context.getThingId(), command.getFeatureId(),
                        command.getDittoHeaders()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void deleteFeaturePropertiesFromFeatureWithoutProperties() {
        final Feature feature = FLUX_CAPACITOR.removeProperties();
        final CommandStrategy.Context context = getDefaultContext();
        final DeleteFeatureProperties command =
                DeleteFeatureProperties.of(context.getThingId(), feature.getId(), DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V1.setFeature(feature), NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(
                ExceptionFactory.featurePropertiesNotFound(context.getThingId(), command.getFeatureId(),
                        command.getDittoHeaders()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

}
