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
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureDefinition;
import org.eclipse.ditto.signals.commands.things.modify.DeleteFeatureDefinitionResponse;
import org.eclipse.ditto.signals.events.things.FeatureDefinitionDeleted;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link DeleteFeatureDefinitionStrategy}.
 */
public final class DeleteFeatureDefinitionStrategyTest extends AbstractCommandStrategyTest {

    private DeleteFeatureDefinitionStrategy underTest;

    @Before
    public void setUp() {
        underTest = new DeleteFeatureDefinitionStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(DeleteFeatureDefinitionStrategy.class, areImmutable());
    }

    @Test
    public void successfullyDeleteFeatureDefinitionFromFeature() {
        final CommandStrategy.Context context = getDefaultContext();
        final String featureId = FLUX_CAPACITOR_ID;
        final DeleteFeatureDefinition command =
                DeleteFeatureDefinition.of(context.getThingId(), featureId, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V1, NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).containsInstanceOf(FeatureDefinitionDeleted.class);
        assertThat(result.getCommandResponse()).contains(
                DeleteFeatureDefinitionResponse.of(context.getThingId(), featureId, command.getDittoHeaders()));
        assertThat(result.getException()).isEmpty();
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void deleteFeatureDefinitionFromThingWithoutFeatures() {
        final CommandStrategy.Context context = getDefaultContext();
        final DeleteFeatureDefinition command =
                DeleteFeatureDefinition.of(context.getThingId(), FLUX_CAPACITOR_ID, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V1.removeFeatures(), NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(
                ExceptionFactory.featureNotFound(context.getThingId(), command.getFeatureId(),
                        command.getDittoHeaders()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void deleteFeatureDefinitionFromThingWithoutThatFeature() {
        final String featureId = FLUX_CAPACITOR_ID;
        final CommandStrategy.Context context = getDefaultContext();
        final DeleteFeatureDefinition command =
                DeleteFeatureDefinition.of(context.getThingId(), featureId, DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V1.removeFeature(featureId), NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(
                ExceptionFactory.featureNotFound(context.getThingId(), command.getFeatureId(),
                        command.getDittoHeaders()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

    @Test
    public void deleteFeatureDefinitionFromFeatureWithoutDefinition() {
        final Feature feature = FLUX_CAPACITOR.removeDefinition();
        final CommandStrategy.Context context = getDefaultContext();
        final DeleteFeatureDefinition command =
                DeleteFeatureDefinition.of(context.getThingId(), feature.getId(), DittoHeaders.empty());

        final CommandStrategy.Result result = underTest.doApply(context, THING_V1.setFeature(feature), NEXT_REVISION, command);

        assertThat(result.getEventToPersist()).isEmpty();
        assertThat(result.getCommandResponse()).isEmpty();
        assertThat(result.getException()).contains(
                ExceptionFactory.featureDefinitionNotFound(context.getThingId(), command.getFeatureId(),
                        command.getDittoHeaders()));
        assertThat(result.isBecomeDeleted()).isFalse();
    }

}
