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
import static org.eclipse.ditto.things.model.TestConstants.Thing.THING_ID;
import static org.eclipse.ditto.things.model.TestConstants.Thing.THING_V2;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.json.FieldType;
import org.eclipse.ditto.base.model.json.JsonSchemaVersion;
import org.eclipse.ditto.internal.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.things.api.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.things.model.TestConstants;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.service.persistence.actors.ETagTestUtils;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link SudoRetrieveThingStrategy}.
 */
public final class SudoRetrieveThingStrategyTest extends AbstractCommandStrategyTest {

    private SudoRetrieveThingStrategy underTest;

    @Before
    public void setUp() {
        underTest = new SudoRetrieveThingStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(SudoRetrieveThingStrategy.class, areImmutable());
    }

    @Test
    public void isNotDefinedForDeviantThingIds() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final SudoRetrieveThing command =
                SudoRetrieveThing.of(ThingId.of("org.example", "myThing"), DittoHeaders.empty());

        final boolean defined = underTest.isDefined(context, THING_V2, command);

        assertThat(defined).isFalse();
    }

    @Test
    public void isNotDefinedIfContextHasNoThing() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final SudoRetrieveThing command = SudoRetrieveThing.of(THING_ID, DittoHeaders.empty());

        final boolean defined = underTest.isDefined(context, null, command);

        assertThat(defined).isFalse();
    }

    @Test
    public void isDefinedIfContextHasThingAndThingIdsAreEqual() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final SudoRetrieveThing command = SudoRetrieveThing.of(context.getState(), DittoHeaders.empty());

        final boolean defined = underTest.isDefined(context, THING_V2, command);

        assertThat(defined).isTrue();
    }

    @Test
    public void retrieveThingWithoutSelectedFields() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .schemaVersion(JsonSchemaVersion.V_2)
                .build();
        final SudoRetrieveThing command = SudoRetrieveThing.of(context.getState(), dittoHeaders);
        final JsonObject expectedThingJson = THING_V2.toJson(command.getImplementedSchemaVersion(),
                FieldType.regularOrSpecial());
        final SudoRetrieveThingResponse expectedResponse =
                ETagTestUtils.sudoRetrieveThingResponse(THING_V2, expectedThingJson, dittoHeaders);

        assertQueryResult(underTest, THING_V2, command, expectedResponse);
    }

    @Test
    public void retrieveThingWithoutSelectedFieldsWithOriginalSchemaVersion() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final SudoRetrieveThing command =
                SudoRetrieveThing.withOriginalSchemaVersion(context.getState(), null, DittoHeaders.empty());
        final JsonObject expectedThingJson = THING_V2.toJson(THING_V2.getImplementedSchemaVersion(),
                FieldType.regularOrSpecial());
        final SudoRetrieveThingResponse expectedResponse =
                ETagTestUtils.sudoRetrieveThingResponse(THING_V2, expectedThingJson, DittoHeaders.empty());

        assertQueryResult(underTest, THING_V2, command, expectedResponse);
    }

    @Test
    public void retrieveThingWithSelectedFields() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final JsonFieldSelector fieldSelector = JsonFactory.newFieldSelector("/attributes/location");
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .schemaVersion(JsonSchemaVersion.V_2)
                .build();
        final SudoRetrieveThing command =
                SudoRetrieveThing.of(context.getState(), fieldSelector, dittoHeaders);
        final JsonObject expectedThingJson = THING_V2.toJson(command.getImplementedSchemaVersion(), fieldSelector,
                FieldType.regularOrSpecial());
        final SudoRetrieveThingResponse expectedResponse =
                ETagTestUtils.sudoRetrieveThingResponse(THING_V2, expectedThingJson, dittoHeaders);

        assertQueryResult(underTest, THING_V2, command, expectedResponse);
    }

    @Test
    public void retrieveThingWithSelectedFieldsWithFeatureWildcard() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final JsonFieldSelector fieldSelector = JsonFactory.newFieldSelector("/attributes/location",
                "/features/*/properties/target_year_1");
        final JsonFieldSelector expandedFieldSelector = JsonFactory.newFieldSelector("/attributes/location",
                "/features/" + TestConstants.Feature.FLUX_CAPACITOR_ID + "/properties/target_year_1",
                "/features/f1/properties/target_year_1",
                "/features/f2/properties/target_year_1");
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .schemaVersion(JsonSchemaVersion.V_2)
                .build();
        final Thing thing = THING_V2.toBuilder()
                .setFeature("f1", ThingsModelFactory.newFeaturePropertiesBuilder().set("target_year_1", 2022).build())
                .setFeature("f2", ThingsModelFactory.newFeaturePropertiesBuilder().set("connected", false).build())
                .build();

        final SudoRetrieveThing command = SudoRetrieveThing.of(context.getState(), fieldSelector, dittoHeaders);
        final JsonObject expectedThingJson = thing.toJson(command.getImplementedSchemaVersion(), expandedFieldSelector,
                FieldType.regularOrSpecial());
        final SudoRetrieveThingResponse expectedResponse =
                ETagTestUtils.sudoRetrieveThingResponse(thing, expectedThingJson, dittoHeaders);

        assertQueryResult(underTest, thing, command, expectedResponse);
    }

    @Test
    public void retrieveThingWithSelectedFieldsWithOriginalSchemaVersion() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final JsonFieldSelector fieldSelector = JsonFactory.newFieldSelector("/attributes/location");
        final SudoRetrieveThing command =
                SudoRetrieveThing.of(context.getState(), fieldSelector, DittoHeaders.empty());
        final JsonObject expectedThingJson = THING_V2.toJson(THING_V2.getImplementedSchemaVersion(), fieldSelector,
                FieldType.regularOrSpecial());
        final SudoRetrieveThingResponse expectedResponse =
                ETagTestUtils.sudoRetrieveThingResponse(THING_V2, expectedThingJson, DittoHeaders.empty());

        assertQueryResult(underTest, THING_V2, command, expectedResponse);
    }

    @Test
    public void unhandledReturnsThingNotAccessibleException() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final SudoRetrieveThing command = SudoRetrieveThing.of(context.getState(), DittoHeaders.empty());
        final ThingNotAccessibleException expectedException =
                new ThingNotAccessibleException(context.getState(), command.getDittoHeaders());

        assertUnhandledResult(underTest, THING_V2, command, expectedException);
    }
}
