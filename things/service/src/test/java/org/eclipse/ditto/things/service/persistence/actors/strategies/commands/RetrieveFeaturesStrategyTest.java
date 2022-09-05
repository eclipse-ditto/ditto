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

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.things.model.TestConstants.Feature.FEATURES;
import static org.eclipse.ditto.things.model.TestConstants.Thing.THING_V2;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.exceptions.DittoRuntimeException;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.things.model.FeaturesBuilder;
import org.eclipse.ditto.things.model.TestConstants;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeatures;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveFeaturesResponse;
import org.eclipse.ditto.things.service.persistence.actors.ETagTestUtils;
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
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final RetrieveFeatures command = RetrieveFeatures.of(context.getState(), DittoHeaders.empty());
        final RetrieveFeaturesResponse expectedResponse = ETagTestUtils.retrieveFeaturesResponse(command.getEntityId(), FEATURES,
                FEATURES.toJson(command.getImplementedSchemaVersion()), command.getDittoHeaders());

        assertQueryResult(underTest, THING_V2, command, expectedResponse);
    }

    @Test
    public void retrieveFeaturesWithSelectedFields() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final JsonFieldSelector selectedFields = JsonFactory.newFieldSelector("maker");
        final RetrieveFeatures command =
                RetrieveFeatures.of(context.getState(), selectedFields, DittoHeaders.empty());
        final RetrieveFeaturesResponse expectedResponse = ETagTestUtils.retrieveFeaturesResponse(command.getEntityId(), FEATURES,
                FEATURES.toJson(command.getImplementedSchemaVersion(), selectedFields), command.getDittoHeaders());

        assertQueryResult(underTest, THING_V2, command, expectedResponse);
    }

    @Test
    public void retrieveFeaturesFromThingWithoutFeatures() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final RetrieveFeatures command = RetrieveFeatures.of(context.getState(), DittoHeaders.empty());
        final DittoRuntimeException expectedException =
                ExceptionFactory.featuresNotFound(command.getEntityId(), command.getDittoHeaders());

        assertErrorResult(underTest, THING_V2.removeFeatures(), command, expectedException);
    }

    @Test
    public void retrieveFeaturesWithSelectedFieldsWithFeatureWildcard() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final JsonFieldSelector selectedFields = JsonFactory.newFieldSelector("/f3/properties/location",
                "/*/properties/target_year_1");
        final RetrieveFeatures command =
                RetrieveFeatures.of(context.getState(), selectedFields, DittoHeaders.empty());

        final FeaturesBuilder featuresBuilder = ThingsModelFactory.newFeaturesBuilder();
        THING_V2.getFeatures().orElseThrow().forEach(featuresBuilder::set);
        featuresBuilder.set(ThingsModelFactory.newFeature("f1",
                ThingsModelFactory.newFeaturePropertiesBuilder().set("target_year_1", 2022).build()));
        featuresBuilder.set(ThingsModelFactory.newFeature("f2",
                ThingsModelFactory.newFeaturePropertiesBuilder().set("connected", false).build()));
        final Thing thing = THING_V2.toBuilder()
                .setFeatures(featuresBuilder.build())
                .build();

        final JsonFieldSelector expandedFieldSelector = JsonFactory.newFieldSelector("/f3/properties/location",
                "/" + TestConstants.Feature.FLUX_CAPACITOR_ID + "/properties/target_year_1",
                "/f1/properties/target_year_1",
                "/f2/properties/target_year_1");

        assertQueryResult(underTest, thing, command, response -> {
            assertThat(response).isInstanceOf(RetrieveFeaturesResponse.class);
            final RetrieveFeaturesResponse retrieveFeaturesResponse = (RetrieveFeaturesResponse) response;
            assertThat(retrieveFeaturesResponse.getEntity()).isEqualTo(
                    thing.getFeatures().map(f -> f.toJson(expandedFieldSelector)).orElseThrow());
        });
    }
}
