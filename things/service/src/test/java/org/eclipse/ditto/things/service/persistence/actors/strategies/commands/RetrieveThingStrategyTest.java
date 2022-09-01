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
import static org.eclipse.ditto.things.model.TestConstants.Thing.THING_V2;
import static org.mutabilitydetector.unittesting.AllowedReason.provided;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.things.model.FeaturesBuilder;
import org.eclipse.ditto.things.model.TestConstants;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;
import org.eclipse.ditto.things.service.persistence.actors.ETagTestUtils;
import org.eclipse.ditto.wot.integration.provider.WotThingDescriptionProvider;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;

/**
 * Unit test for {@link RetrieveThingStrategy}.
 */
public final class RetrieveThingStrategyTest extends AbstractCommandStrategyTest {

    private RetrieveThingStrategy underTest;

    @Before
    public void setUp() {
        final ActorSystem system = ActorSystem.create("test", ConfigFactory.load("test"));
        underTest = new RetrieveThingStrategy(system);
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveThingStrategy.class, areImmutable(),
                provided(WotThingDescriptionProvider.class).areAlsoImmutable());
    }

    @Test
    public void isNotDefinedForDeviantThingIds() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final RetrieveThing command = RetrieveThing.of(ThingId.of("org.example", "myThing"), DittoHeaders.empty());

        final boolean defined = underTest.isDefined(context, THING_V2, command);

        assertThat(defined).isFalse();
    }

    @Test
    public void isNotDefinedIfContextHasNoThing() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final RetrieveThing command = RetrieveThing.of(context.getState(), DittoHeaders.empty());

        final boolean defined = underTest.isDefined(context, null, command);

        assertThat(defined).isFalse();
    }

    @Test
    public void isDefinedIfContextHasThingAndThingIdsAreEqual() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final RetrieveThing command = RetrieveThing.of(context.getState(), DittoHeaders.empty());

        final boolean defined = underTest.isDefined(context, THING_V2, command);

        assertThat(defined).isTrue();
    }

    @Test
    public void retrieveThingFromContextIfCommandHasNoSnapshotRevisionAndNoSelectedFields() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final RetrieveThing command = RetrieveThing.of(context.getState(), DittoHeaders.empty());
        final RetrieveThingResponse expectedResponse =
                ETagTestUtils.retrieveThingResponse(THING_V2, null, DittoHeaders.empty());

        assertQueryResult(underTest, THING_V2, command, expectedResponse);
    }

    @Test
    public void retrieveThingFromContextIfCommandHasNoSnapshotRevisionButSelectedFields() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final JsonFieldSelector fieldSelector = JsonFactory.newFieldSelector("/attributes/location");
        final RetrieveThing command = RetrieveThing.getBuilder(context.getState(), DittoHeaders.empty())
                .withSelectedFields(fieldSelector)
                .build();
        final RetrieveThingResponse expectedResponse =
                ETagTestUtils.retrieveThingResponse(THING_V2, fieldSelector, DittoHeaders.empty());

        assertQueryResult(underTest, THING_V2, command, expectedResponse);
    }

    @Test
    public void unhandledReturnsThingNotAccessibleException() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final RetrieveThing command = RetrieveThing.of(context.getState(), DittoHeaders.empty());
        final ThingNotAccessibleException expectedException =
                new ThingNotAccessibleException(command.getEntityId(), command.getDittoHeaders());

        assertUnhandledResult(underTest, THING_V2, command, expectedException);
    }

    @Test
    public void retrieveThingWithSelectedFields() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final JsonFieldSelector fieldSelector = JsonFactory.newFieldSelector("/attributes/location");
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .schemaVersion(JsonSchemaVersion.V_2)
                .build();
        final RetrieveThing command = RetrieveThing.getBuilder(context.getState(), dittoHeaders)
                .withSelectedFields(fieldSelector)
                .build();
        final RetrieveThingResponse expectedResponse =
                ETagTestUtils.retrieveThingResponse(THING_V2, fieldSelector, dittoHeaders);

        assertQueryResult(underTest, THING_V2, command, expectedResponse);
    }

    @Test
    public void retrieveThingWithSelectedFieldsWithFeatureWildcard() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final JsonFieldSelector fieldSelector = JsonFactory.newFieldSelector("/attributes/location",
                "/features/*/properties/target_year_1");
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .schemaVersion(JsonSchemaVersion.V_2)
                .build();

        final FeaturesBuilder featuresBuilder = ThingsModelFactory.newFeaturesBuilder();
        THING_V2.getFeatures().orElseThrow().forEach(featuresBuilder::set);
        featuresBuilder.set(ThingsModelFactory.newFeature("f1",
                ThingsModelFactory.newFeaturePropertiesBuilder().set("target_year_1", 2022).build()));
        featuresBuilder.set(ThingsModelFactory.newFeature("f2",
                ThingsModelFactory.newFeaturePropertiesBuilder().set("connected", false).build()));
        final Thing thing = THING_V2.toBuilder()
                .setFeatures(featuresBuilder.build())
                .build();

        final RetrieveThing command = RetrieveThing.getBuilder(context.getState(), dittoHeaders)
                .withSelectedFields(fieldSelector)
                .build();

        final JsonFieldSelector expandedFieldSelector = JsonFactory.newFieldSelector("/attributes/location",
                "/features/" + TestConstants.Feature.FLUX_CAPACITOR_ID + "/properties/target_year_1",
                "/features/f1/properties/target_year_1",
                "/features/f2/properties/target_year_1");

        assertQueryResult(underTest, thing, command, response -> {
            assertThat(response).isInstanceOf(RetrieveThingResponse.class);
            final RetrieveThingResponse retrieveThingResponse = (RetrieveThingResponse) response;
            assertThat(retrieveThingResponse.getEntity()).isEqualTo(thing.toJson(expandedFieldSelector));
        });
    }

}
