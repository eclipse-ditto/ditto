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
package org.eclipse.ditto.things.service.persistence.actors;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.ditto.base.api.persistence.cleanup.CleanupPersistence;
import org.eclipse.ditto.base.api.persistence.cleanup.CleanupPersistenceResponse;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.events.EventsourcedEvent;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThingResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteThing;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteThingResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThingResponse;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import akka.testkit.javadsl.TestKit;

/**
 * Unit test for the cleanup of {@link ThingPersistenceActor}.
 */
public final class ThingPersistenceActorCleanupTest extends PersistenceActorTestBaseWithSnapshotting {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    private static final Logger LOGGER = LoggerFactory.getLogger(ThingPersistenceActorCleanupTest.class);

    @Rule
    public final TestWatcher watchman = new TestedMethodLoggingWatcher(LOGGER);

    @Test
    public void cleanupDeletesUntilButExcludingLatestSnapshot() {
        setup(createNewDefaultTestConfig());
        final List<EventsourcedEvent<?>> expectedEvents = new ArrayList<>();
        final LinkedList<Thing> expectedSnapshots = new LinkedList<>();

        new TestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final ThingId thingId =
                        thing.getEntityId().orElseThrow(() -> new IllegalStateException("ID must not be null!"));
                final ActorRef persistenceActorUnderTest = createPersistenceActorFor(thingId);

                // create a thing...
                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                persistenceActorUnderTest.tell(createThing, getRef());
                final CreateThingResponse createThingResponse = expectMsgClass(dilated(Duration.ofSeconds(5)),
                        CreateThingResponse.class);
                final Thing thingCreated = createThingResponse.getThingCreated()
                        .orElseThrow(IllegalStateException::new);
                assertThingInResponse(thingCreated, thing, 1);
                // ...and verify journal and snapshot state
                expectedEvents.add(toEvent(createThing, thingCreated));
                assertJournal(thingId, expectedEvents);
                assertSnapshotsEmpty(thingId);

                // send multiple ModifyThing commands...
                long latestSnapshot = 0;
                for (int i = 2; i < 12; i++) {

                    final Thing modifiedThing = ThingsModelFactory.newThingBuilder(thing)
                            .setAttribute(JsonFactory.newPointer("foo/bar"), JsonFactory.newValue("baz" + i))
                            .setRevision(i)
                            .build();

                    expectedEvents.add(sendModifyThing(modifiedThing, persistenceActorUnderTest, i));

                    if (i % DEFAULT_TEST_SNAPSHOT_THRESHOLD == 0) {
                        expectedSnapshots.add(modifiedThing);
                        latestSnapshot = modifiedThing.getRevision()
                                .orElseThrow(() -> new IllegalStateException("Expected a revision.")).toLong();
                    }

                    // ...and verify journal and snapshot state after each update
                    assertJournal(thingId, expectedEvents);
                    assertSnapshots(thingId, expectedSnapshots);
                }

                // tell the persistence actor to clean up
                persistenceActorUnderTest.tell(CleanupPersistence.of(thingId, DittoHeaders.empty()), getRef());
                expectMsg(CleanupPersistenceResponse.success(thingId, DittoHeaders.empty()));

                // we expect only the latest snapshot to exist after cleanup
                final List<Thing> expectedSnapshotsAfterCleanup =
                        Collections.singletonList(expectedSnapshots.getLast());
                assertSnapshots(thingId, expectedSnapshotsAfterCleanup);

                // only events after the latest snapshot should survive
                final long revision = latestSnapshot;
                final List<EventsourcedEvent<?>> expectedEventsAfterCleanup = expectedEvents.stream()
                        .filter(e -> e.getRevision() > revision)
                        .collect(Collectors.toList());
                assertJournal(thingId, expectedEventsAfterCleanup);
            }

            private EventsourcedEvent<?> sendModifyThing(final Thing modifiedThing, final ActorRef underTest,
                    final int revisionNumber) {
                final ModifyThing modifyThingCommand =
                        ModifyThing.of(modifiedThing.getEntityId()
                                        .orElseThrow(() -> new IllegalStateException("ID must not be null!")),
                                modifiedThing, null, dittoHeadersV2);
                underTest.tell(modifyThingCommand, getRef());

                expectMsgEquals(modifyThingResponse(modifiedThing, dittoHeadersV2));

                return toEvent(modifyThingCommand, modifiedThing);
            }
        };
    }

    @Test
    public void testDeletedThingIsCleanedUpCorrectly() {
        setup(createNewDefaultTestConfig());
        final List<EventsourcedEvent<?>> expectedEvents = new ArrayList<>();

        new TestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final ThingId thingId =
                        thing.getEntityId().orElseThrow(() -> new IllegalStateException("ID must not be null!"));
                final ActorRef persistenceActorUnderTest = createPersistenceActorFor(thingId);

                // create a thing...
                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                persistenceActorUnderTest.tell(createThing, getRef());
                final CreateThingResponse createThingResponse = expectMsgClass(dilated(Duration.ofSeconds(5)),
                        CreateThingResponse.class);
                final Thing createdThing = createThingResponse.getThingCreated()
                        .orElseThrow(IllegalStateException::new);
                assertThingInResponse(createdThing, thing, 1);
                // ...and verify journal and snapshot state
                final EventsourcedEvent<?> thingCreated = toEvent(createThing, createdThing);
                expectedEvents.add(thingCreated);
                assertJournal(thingId, expectedEvents);
                assertSnapshotsEmpty(thingId);

                // delete the thing...
                final DeleteThing deleteThing = DeleteThing.of(thingId, dittoHeadersV2);
                persistenceActorUnderTest.tell(deleteThing, getRef());
                final DeleteThingResponse deleteThingResponse = expectMsgClass(DeleteThingResponse.class);

                final Thing expectedDeletedSnapshot = toDeletedThing(2);
                assertSnapshots(thingId, Collections.singletonList(expectedDeletedSnapshot));
                final EventsourcedEvent<?> deletedEvent = toEvent(deleteThing, expectedDeletedSnapshot);
                expectedEvents.add(deletedEvent);
                assertJournal(thingId, expectedEvents);

                // tell the persistence actor to clean up
                persistenceActorUnderTest.tell(CleanupPersistence.of(thingId, DittoHeaders.empty()), getRef());
                expectMsg(CleanupPersistenceResponse.success(thingId, DittoHeaders.empty()));
                // we expect only the latest snapshot and latest event to exist after cleanup
                expectedEvents.remove(thingCreated);
                assertSnapshots(thingId, Collections.singletonList(expectedDeletedSnapshot));
                assertJournal(thingId, expectedEvents);
            }
        };
    }

    private static ModifyThingResponse modifyThingResponse(final Thing modifiedThing,
            final DittoHeaders dittoHeaders) {
        final DittoHeaders dittoHeadersWithETag = ETagTestUtils.appendETagToDittoHeaders(modifiedThing, dittoHeaders);
        return ModifyThingResponse.modified(modifiedThing.getEntityId().get(), dittoHeadersWithETag);
    }

}
