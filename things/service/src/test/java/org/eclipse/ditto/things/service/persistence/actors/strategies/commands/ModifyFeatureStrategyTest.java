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
package org.eclipse.ditto.things.service.persistence.actors.strategies.commands;

import static org.eclipse.ditto.things.model.TestConstants.Thing.THING_V2;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.things.model.Feature;
import org.eclipse.ditto.things.model.TestConstants;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyFeature;
import org.eclipse.ditto.things.model.signals.events.FeatureCreated;
import org.eclipse.ditto.things.model.signals.events.FeatureModified;
import org.eclipse.ditto.things.service.persistence.actors.ETagTestUtils;
import org.eclipse.ditto.wot.integration.provider.WotThingDescriptionProvider;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;

/**
 * Unit test for {@link ModifyFeatureStrategy}.
 */
public final class ModifyFeatureStrategyTest extends AbstractCommandStrategyTest {

    private static Feature modifiedFeature;

    private ModifyFeatureStrategy underTest;

    @BeforeClass
    public static void initTestFixture() {
        modifiedFeature = TestConstants.Feature.FLUX_CAPACITOR.setProperty("speed/km", 300000);
    }

    @Before
    public void setUp() {
        final ActorSystem system = ActorSystem.create("test", ConfigFactory.load("test"));
        underTest = new ModifyFeatureStrategy(system);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(ModifyFeatureStrategy.class, areImmutable(),
                provided(WotThingDescriptionProvider.class).areAlsoImmutable());
    }

    @Test
    public void modifyFeatureOnThingWithoutFeatures() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final ModifyFeature command = ModifyFeature.of(context.getState(), modifiedFeature, DittoHeaders.empty());

        assertModificationResult(underTest, THING_V2.removeFeatures(), command,
                FeatureCreated.class,
                ETagTestUtils.modifyFeatureResponse(context.getState(), command.getFeature(), command.getDittoHeaders(), true));
    }

    @Test
    public void modifyFeatureOnThingWithoutThatFeature() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final ModifyFeature command = ModifyFeature.of(context.getState(), modifiedFeature, DittoHeaders.empty());

        assertModificationResult(underTest, THING_V2.removeFeature(modifiedFeature.getId()), command,
                FeatureCreated.class,
                ETagTestUtils.modifyFeatureResponse(context.getState(), command.getFeature(), command.getDittoHeaders(), true));
    }

    @Test
    public void modifyExistingFeature() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final ModifyFeature command = ModifyFeature.of(context.getState(), modifiedFeature, DittoHeaders.empty());

        assertModificationResult(underTest, THING_V2, command,
                FeatureModified.class,
                ETagTestUtils.modifyFeatureResponse(context.getState(), command.getFeature(), command.getDittoHeaders(), false));
    }

}
