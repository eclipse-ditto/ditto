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
package org.eclipse.ditto.services.things.persistence.actors;

import static org.eclipse.ditto.services.things.persistence.actors.ETagTestUtils.appendETagToDittoHeaders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.model.base.entity.id.DefaultEntityId;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.model.things.id.ThingId;
import org.eclipse.ditto.signals.commands.cleanup.CleanupPersistence;
import org.eclipse.ditto.signals.commands.cleanup.CleanupPersistenceResponse;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.eclipse.ditto.signals.commands.things.modify.CreateThingResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThing;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThingResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThingResponse;
import org.eclipse.ditto.signals.events.base.Event;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(ThingPersistenceActorCleanupTest.class);

    @Rule
    public final TestWatcher watchman = new TestedMethodLoggingWatcher(LOGGER);

    @Test
    public void cleanupDeletesUntilButExcludingLatestSnapshot() {
        setup(createNewDefaultTestConfig());
        final List<Event> expectedEvents = new ArrayList<>();
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
                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                final Thing thingCreated = createThingResponse.getThingCreated()
                        .orElseThrow(IllegalStateException::new);
                assertThingInResponse(thingCreated, thing, 1);
                // ...and verify journal and snapshot state
                expectedEvents.add(toEvent(createThing, 1));
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
                expectMsg(CleanupPersistenceResponse.success(
                        DefaultEntityId.of(ThingPersistenceActor.PERSISTENCE_ID_PREFIX + thingId),
                        DittoHeaders.empty()));

                // we expect only the latest snapshot to exist after cleanup
                final List<Thing> expectedSnapshotsAfterCleanup =
                        Collections.singletonList(expectedSnapshots.getLast());
                assertSnapshots(thingId, expectedSnapshotsAfterCleanup);

                // only events after the latest snapshot should survive
                final long revision = latestSnapshot;
                final List<Event> expectedEventsAfterCleanup = expectedEvents.stream()
                        .filter(e -> e.getRevision() > revision)
                        .collect(Collectors.toList());
                assertJournal(thingId, expectedEventsAfterCleanup);
            }

            private Event sendModifyThing(final Thing modifiedThing, final ActorRef underTest,
                    final int revisionNumber) {
                final ModifyThing modifyThingCommand =
                        ModifyThing.of(modifiedThing.getEntityId()
                                        .orElseThrow(() -> new IllegalStateException("ID must not be null!")),
                                modifiedThing, null, dittoHeadersV2);
                underTest.tell(modifyThingCommand, getRef());
                expectMsgEquals(modifyThingResponse(modifiedThing, dittoHeadersV2));

                return toEvent(modifyThingCommand, revisionNumber);
            }
        };
    }

    @Test
    public void testDeletedThingIsCleanedUpCorrectly() {
        setup(createNewDefaultTestConfig());
        final List<Event> expectedEvents = new ArrayList<>();

        new TestKit(actorSystem) {{
                final Thing thing = createThingV2WithRandomId();
            final ThingId thingId =
                    thing.getEntityId().orElseThrow(() -> new IllegalStateException("ID must not be null!"));
                final ActorRef persistenceActorUnderTest = createPersistenceActorFor(thingId);

                // create a thing...
                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                persistenceActorUnderTest.tell(createThing, getRef());
                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                final Thing thingCreated = createThingResponse.getThingCreated()
                        .orElseThrow(IllegalStateException::new);
                assertThingInResponse(thingCreated, thing, 1);
                // ...and verify journal and snapshot state
                expectedEvents.add(toEvent(createThing, 1));
                assertJournal(thingId, expectedEvents);
                assertSnapshotsEmpty(thingId);

                // delete the thing...
                final DeleteThing deleteThing = DeleteThing.of(thingId, dittoHeadersV2);
                persistenceActorUnderTest.tell(deleteThing, getRef());
                final DeleteThingResponse deleteThingResponse = expectMsgClass(DeleteThingResponse.class);

                final Thing expectedDeletedSnapshot = toDeletedThing(thingCreated, 2);
                assertSnapshots(thingId, Collections.singletonList(expectedDeletedSnapshot));
                final Event deletedEvent = toEvent(deleteThing, 2);
                expectedEvents.add(deletedEvent);
                assertJournal(thingId, expectedEvents);

                // tell the persistence actor to clean up
                persistenceActorUnderTest.tell(CleanupPersistence.of(thingId, DittoHeaders.empty()), getRef());
            expectMsg(CleanupPersistenceResponse.success(
                    DefaultEntityId.of(ThingPersistenceActor.PERSISTENCE_ID_PREFIX + thingId), DittoHeaders.empty()));

                // we expect only the latest snapshot to exist after cleanup
                assertSnapshots(thingId, Collections.singletonList(expectedDeletedSnapshot));
                assertJournal(thingId, Collections.emptyList());
            }
        };
    }

    private static ModifyThingResponse modifyThingResponse(final Thing modifiedThing,
            final DittoHeaders dittoHeaders) {
        final DittoHeaders dittoHeadersWithETag = appendETagToDittoHeaders(modifiedThing, dittoHeaders);
        return ModifyThingResponse.modified(modifiedThing.getEntityId().get(), dittoHeadersWithETag);
    }
}
