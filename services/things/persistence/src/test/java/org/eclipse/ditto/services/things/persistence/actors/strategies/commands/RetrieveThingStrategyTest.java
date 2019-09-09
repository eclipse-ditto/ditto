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

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V2;
import static org.eclipse.ditto.services.things.persistence.actors.ETagTestUtils.retrieveThingResponse;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.ThingId;
import org.eclipse.ditto.services.utils.persistentactors.commands.CommandStrategy;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit test for {@link RetrieveThingStrategy}.
 */
public final class RetrieveThingStrategyTest extends AbstractCommandStrategyTest {

    private RetrieveThingStrategy underTest;

    @Before
    public void setUp() {
        underTest = new RetrieveThingStrategy();
    }

    @Test
    public void assertImmutability() {
        assertInstancesOf(RetrieveThingStrategy.class, areImmutable());
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
        final RetrieveThing command = RetrieveThing.of(context.getEntityId(), DittoHeaders.empty());

        final boolean defined = underTest.isDefined(context, null, command);

        assertThat(defined).isFalse();
    }

    @Test
    public void isDefinedIfContextHasThingAndThingIdsAreEqual() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final RetrieveThing command = RetrieveThing.of(context.getEntityId(), DittoHeaders.empty());

        final boolean defined = underTest.isDefined(context, THING_V2, command);

        assertThat(defined).isTrue();
    }

    @Test
    public void retrieveThingFromContextIfCommandHasNoSnapshotRevisionAndNoSelectedFields() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final RetrieveThing command = RetrieveThing.of(context.getEntityId(), DittoHeaders.empty());
        final JsonObject expectedThingJson = THING_V2.toJson(command.getImplementedSchemaVersion());
        final RetrieveThingResponse expectedResponse =
                retrieveThingResponse(THING_V2, expectedThingJson, DittoHeaders.empty());

        assertQueryResult(underTest, THING_V2, command, expectedResponse);
    }

    @Test
    public void retrieveThingFromContextIfCommandHasNoSnapshotRevisionButSelectedFields() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final JsonFieldSelector fieldSelector = JsonFactory.newFieldSelector("/attribute/location");
        final RetrieveThing command = RetrieveThing.getBuilder(context.getEntityId(), DittoHeaders.empty())
                .withSelectedFields(fieldSelector)
                .build();
        final JsonObject expectedThingJson = THING_V2.toJson(command.getImplementedSchemaVersion(), fieldSelector);
        final RetrieveThingResponse expectedResponse =
                retrieveThingResponse(THING_V2, expectedThingJson, DittoHeaders.empty());

        assertQueryResult(underTest, THING_V2, command, expectedResponse);
    }

    @Test
    public void unhandledReturnsThingNotAccessibleException() {
        final CommandStrategy.Context<ThingId> context = getDefaultContext();
        final RetrieveThing command = RetrieveThing.of(context.getEntityId(), DittoHeaders.empty());
        final ThingNotAccessibleException expectedException =
                new ThingNotAccessibleException(command.getThingEntityId(), command.getDittoHeaders());

        assertUnhandledResult(underTest, THING_V2, command, expectedException);
    }

}
