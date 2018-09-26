/*
 * Copyright (c) 2017-2018 Bosch Software Innovations GmbH.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-2.0/index.php
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.ditto.services.things.persistence.actors.strategies.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.ditto.model.things.TestConstants.Thing.THING_V2;
import static org.eclipse.ditto.services.things.persistence.actors.ETagTestUtils.retrieveThingResponse;
import static org.mutabilitydetector.unittesting.MutabilityAssert.assertInstancesOf;
import static org.mutabilitydetector.unittesting.MutabilityMatchers.areImmutable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingUnavailableException;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

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
        final CommandStrategy.Context context = getDefaultContext();
        final RetrieveThing command = RetrieveThing.of("org.example:myThing", DittoHeaders.empty());

        final boolean defined = underTest.isDefined(context, THING_V2, command);

        assertThat(defined).isFalse();
    }

    @Test
    public void isNotDefinedIfContextHasNoThing() {
        final CommandStrategy.Context context = getDefaultContext();
        final RetrieveThing command = RetrieveThing.of(context.getThingId(), DittoHeaders.empty());

        final boolean defined = underTest.isDefined(context, null, command);

        assertThat(defined).isFalse();
    }

    @Test
    public void isDefinedIfContextHasThingAndThingIdsAreEqual() {
        final CommandStrategy.Context context = getDefaultContext();
        final RetrieveThing command = RetrieveThing.of(context.getThingId(), DittoHeaders.empty());

        final boolean defined = underTest.isDefined(context, THING_V2, command);

        assertThat(defined).isTrue();
    }

    @Test
    public void retrieveThingFromContextIfCommandHasNoSnapshotRevisionAndNoSelectedFields() {
        final CommandStrategy.Context context = getDefaultContext();
        final RetrieveThing command = RetrieveThing.of(context.getThingId(), DittoHeaders.empty());
        final JsonObject expectedThingJson = THING_V2.toJson(command.getImplementedSchemaVersion());
        final RetrieveThingResponse expectedResponse =
                retrieveThingResponse(THING_V2, expectedThingJson, DittoHeaders.empty());

        assertQueryResult(underTest, THING_V2, command, expectedResponse);
    }

    @Test
    public void retrieveThingFromContextIfCommandHasNoSnapshotRevisionButSelectedFields() {
        final CommandStrategy.Context context = getDefaultContext();
        final JsonFieldSelector fieldSelector = JsonFactory.newFieldSelector("/attribute/location");
        final RetrieveThing command = RetrieveThing.getBuilder(context.getThingId(), DittoHeaders.empty())
                .withSelectedFields(fieldSelector)
                .build();
        final JsonObject expectedThingJson = THING_V2.toJson(command.getImplementedSchemaVersion(), fieldSelector);
        final RetrieveThingResponse expectedResponse =
                retrieveThingResponse(THING_V2, expectedThingJson, DittoHeaders.empty());

        assertQueryResult(underTest, THING_V2, command, expectedResponse);
    }

    @Test
    public void retrieveThingFromSnapshotterWithoutSelectedFields() {
        final CommandStrategy.Context context = getDefaultContext();
        final long snapshotRevision = 1337L;
        final RetrieveThing command = RetrieveThing.getBuilder(context.getThingId(), DittoHeaders.empty())
                .withSnapshotRevision(snapshotRevision)
                .build();
        Mockito.when(thingSnapshotter.loadSnapshot(snapshotRevision))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(THING_V2)));
        final JsonObject expectedThingJson = THING_V2.toJson(command.getImplementedSchemaVersion());
        // NOTE: ETags are not supported for snapshots
        final RetrieveThingResponse expectedResponse =
                RetrieveThingResponse.of(context.getThingId(), expectedThingJson, DittoHeaders.empty());

        assertFutureResult(underTest, THING_V2, command, expectedResponse);
    }

    @Test
    public void retrieveThingFromSnapshotterWithSelectedFields() {
        final CommandStrategy.Context context = getDefaultContext();
        final long snapshotRevision = 1337L;
        final JsonFieldSelector fieldSelector = JsonFactory.newFieldSelector("/attribute/location");
        final RetrieveThing command = RetrieveThing.getBuilder(context.getThingId(), DittoHeaders.empty())
                .withSnapshotRevision(snapshotRevision)
                .withSelectedFields(fieldSelector)
                .build();
        Mockito.when(thingSnapshotter.loadSnapshot(snapshotRevision))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(THING_V2)));
        final JsonObject expectedThingJson = THING_V2.toJson(command.getImplementedSchemaVersion(), fieldSelector);
        // NOTE: ETags are not supported for snapshots
        final RetrieveThingResponse expectedResponse =
                RetrieveThingResponse.of(context.getThingId(), expectedThingJson, DittoHeaders.empty());

        assertFutureResult(underTest, THING_V2, command, expectedResponse);
    }

    @Test
    public void retrieveNonExistentThingFromSnapshotter() {
        final CommandStrategy.Context context = getDefaultContext();
        final long snapshotRevision = 1337L;
        final JsonFieldSelector fieldSelector = JsonFactory.newFieldSelector("/attribute/location");
        final RetrieveThing command = RetrieveThing.getBuilder(context.getThingId(), DittoHeaders.empty())
                .withSnapshotRevision(snapshotRevision)
                .withSelectedFields(fieldSelector)
                .build();
        Mockito.when(thingSnapshotter.loadSnapshot(snapshotRevision))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        final ThingNotAccessibleException expectedException =
                new ThingNotAccessibleException(command.getThingId(), command.getDittoHeaders());

        assertFutureResult(underTest, THING_V2, command, expectedException);
    }

    @Test
    public void retrieveThingWithException() {
        final CommandStrategy.Context context = getDefaultContext();
        final long snapshotRevision = 1337L;
        final RetrieveThing command = RetrieveThing.getBuilder(context.getThingId(), DittoHeaders.empty())
                .withSnapshotRevision(snapshotRevision)
                .build();

        final CompletableFuture<Optional<Thing>> completableFuture = new CompletableFuture<>();
        completableFuture.completeExceptionally(new IllegalArgumentException());

        Mockito.when(thingSnapshotter.loadSnapshot(snapshotRevision)).thenReturn(completableFuture);

        final ThingUnavailableException expectedException = ThingUnavailableException
                .newBuilder(command.getThingId())
                .dittoHeaders(command.getDittoHeaders())
                .build();

        assertFutureResult(underTest, THING_V2, command, expectedException);
    }

    @Test
    public void unhandledReturnsThingNotAccessibleException() {
        final CommandStrategy.Context context = getDefaultContext();
        final RetrieveThing command = RetrieveThing.of(context.getThingId(), DittoHeaders.empty());
        final ThingNotAccessibleException expectedException =
                new ThingNotAccessibleException(command.getThingId(), command.getDittoHeaders());

        assertUnhandledResult(underTest, THING_V2, command, expectedException);
    }

}
