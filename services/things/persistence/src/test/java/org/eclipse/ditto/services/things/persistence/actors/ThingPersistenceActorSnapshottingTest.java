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
package org.eclipse.ditto.services.things.persistence.actors;

import static org.eclipse.ditto.signals.events.things.assertions.ThingEventAssertions.assertThat;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import org.awaitility.Awaitility;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingLifecycle;
import org.eclipse.ditto.model.things.ThingRevision;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.model.things.assertions.DittoThingsAssertions;
import org.eclipse.ditto.services.things.persistence.serializer.ThingMongoEventAdapter;
import org.eclipse.ditto.services.things.persistence.testhelper.Assertions;
import org.eclipse.ditto.services.things.persistence.testhelper.ThingsJournalTestHelper;
import org.eclipse.ditto.services.things.persistence.testhelper.ThingsSnapshotTestHelper;
import org.eclipse.ditto.services.things.starter.util.ConfigKeys;
import org.eclipse.ditto.services.utils.persistence.mongo.DittoBsonJson;
import org.eclipse.ditto.services.utils.test.Retry;
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.assertions.ThingCommandAssertions;
import org.eclipse.ditto.signals.commands.things.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.eclipse.ditto.signals.commands.things.modify.CreateThingResponse;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThing;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThingResponse;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThingResponse;
import org.eclipse.ditto.signals.commands.things.modify.TagThing;
import org.eclipse.ditto.signals.commands.things.modify.TagThingResponse;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThing;
import org.eclipse.ditto.signals.commands.things.query.RetrieveThingResponse;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.things.ThingCreated;
import org.eclipse.ditto.signals.events.things.ThingDeleted;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.eclipse.ditto.signals.events.things.ThingModified;
import org.junit.Test;

import com.mongodb.DBObject;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import akka.actor.ActorRef;
import akka.actor.ExtendedActorSystem;
import akka.actor.PoisonPill;
import akka.testkit.javadsl.TestKit;
import scala.concurrent.duration.FiniteDuration;

/**
 * Unit test for the snapshotting functionality of {@link ThingPersistenceActor}.
 */
public final class ThingPersistenceActorSnapshottingTest extends PersistenceActorTestBase {

    private static final int DEFAULT_TEST_SNAPSHOT_THRESHOLD = 2;
    private static final int NEVER_TAKE_SNAPSHOT_THRESHOLD = Integer.MAX_VALUE;
    private static final boolean DEFAULT_TEST_SNAPSHOT_DELETE_OLD = true;
    private static final boolean DEFAULT_TEST_EVENTS_DELETE_OLD = true;
    private static final Duration VERY_LONG_DURATION = Duration.ofDays(100);
    private static final int PERSISTENCE_ASSERT_WAIT_AT_MOST_MS = 3000;
    private static final long PERSISTENCE_ASSERT_RETRY_DELAY_MS = 500;

    private static final JsonFieldSelector FIELD_SELECTOR = JsonFactory.newFieldSelector(Thing.JsonFields.ATTRIBUTES,
            Thing.JsonFields.FEATURES, Thing.JsonFields.ID, Thing.JsonFields.MODIFIED, Thing.JsonFields.REVISION,
            Thing.JsonFields.POLICY_ID, Thing.JsonFields.LIFECYCLE);

    private static Config createNewDefaultTestConfig() {
        return ConfigFactory.empty()
                .withValue(ConfigKeys.Thing.SNAPSHOT_THRESHOLD, ConfigValueFactory.fromAnyRef(
                        DEFAULT_TEST_SNAPSHOT_THRESHOLD))
                .withValue(ConfigKeys.Thing.ACTIVITY_CHECK_INTERVAL, ConfigValueFactory.fromAnyRef(VERY_LONG_DURATION))
                .withValue(ConfigKeys.Thing.ACTIVITY_CHECK_DELETED_INTERVAL,
                        ConfigValueFactory.fromAnyRef(VERY_LONG_DURATION))
                .withValue(ConfigKeys.Thing.SNAPSHOT_DELETE_OLD, ConfigValueFactory.fromAnyRef(
                        DEFAULT_TEST_SNAPSHOT_DELETE_OLD))
                .withValue(ConfigKeys.Thing.EVENTS_DELETE_OLD,
                        ConfigValueFactory.fromAnyRef(DEFAULT_TEST_EVENTS_DELETE_OLD))
                .withValue(ConfigKeys.Thing.SNAPSHOT_INTERVAL, ConfigValueFactory.fromAnyRef(VERY_LONG_DURATION));
    }
    
    private ThingMongoEventAdapter eventAdapter;
    private ThingsJournalTestHelper<ThingEvent> journalTestHelper;
    private ThingsSnapshotTestHelper<Thing> snapshotTestHelper;
    private Map<Class<? extends Command>, BiFunction<Command, Long, ThingEvent>> commandToEventMapperRegistry;

    @Override
    protected void setup(final Config customConfig) {
        super.setup(customConfig);
        eventAdapter = new ThingMongoEventAdapter((ExtendedActorSystem) actorSystem);

        journalTestHelper = new ThingsJournalTestHelper<>(actorSystem, this::convertJournalEntryToEvent,
                ThingPersistenceActorSnapshottingTest::convertDomainIdToPersistenceId);
        snapshotTestHelper = new ThingsSnapshotTestHelper<>(actorSystem,
                ThingPersistenceActorSnapshottingTest::convertSnapshotDataToThing,
                ThingPersistenceActorSnapshottingTest::convertDomainIdToPersistenceId);


        commandToEventMapperRegistry = new HashMap<>();
        commandToEventMapperRegistry.put(CreateThing.class, (command, revision) -> {
            final CreateThing createCommand = (CreateThing) command;
            return ThingCreated.of(createCommand.getThing(), revision, DittoHeaders.empty());
        });
        commandToEventMapperRegistry.put(ModifyThing.class, (command, revision) -> {
            final ModifyThing modifyCommand = (ModifyThing) command;
            return ThingModified.of(modifyCommand.getThing(), revision, DittoHeaders.empty());
        });
        commandToEventMapperRegistry.put(DeleteThing.class, (command, revision) -> {
            final DeleteThing deleteCommand = (DeleteThing) command;
            return ThingDeleted.of(deleteCommand.getThingId(), revision, DittoHeaders.empty());
        });
    }

    /**
     * Check that a deleted thing is snapshot correctly and can be recreated. Before the bugfix, the
     * deleted thing was snapshot with incorrect data (previous version), thus it would be handled as created
     * after actor restart.
     */
    @Test
    public void deletedThingIsSnapshotWithCorrectDataAndCanBeRecreated() {
        setup(createNewDefaultTestConfig());

        new TestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final String thingId = thing.getId().orElseThrow(IllegalStateException::new);

                ActorRef underTest = createPersistenceActorFor(thingId);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                final Thing thingCreated = createThingResponse.getThingCreated()
                        .orElseThrow(IllegalStateException::new);
                assertThingInResponse(thingCreated, thing, 1);

                final Event expectedCreatedEvent = toEvent(createThing, 1);
                assertJournal(thingId, Collections.singletonList(expectedCreatedEvent));
                assertSnapshotsEmpty(thingId);

                final DeleteThing deleteThing = DeleteThing.of(thingId, dittoHeadersV2);
                underTest.tell(deleteThing, getRef());
                expectMsgEquals(DeleteThingResponse.of(thingId, dittoHeadersV2));

                final Thing expectedDeletedSnapshot = toDeletedThing(thingCreated, 2);
                assertSnapshots(thingId, Collections.singletonList(expectedDeletedSnapshot));
                final Event expectedDeletedEvent = toEvent(deleteThing, 2);
                // created-event has been deleted due to snapshot
                assertJournal(thingId, Collections.singletonList(expectedDeletedEvent));

                // restart actor to recover thing state: make sure that the snapshot of deleted thing exists and can
                // be restored
                watch(underTest);
                underTest.tell(PoisonPill.getInstance(), getRef());
                expectTerminated(underTest);
                underTest = Retry.untilSuccess(() -> createPersistenceActorFor(thingId));

                final RetrieveThing retrieveThing = RetrieveThing.getBuilder(thingId, dittoHeadersV2)
                        .withSelectedFields(FIELD_SELECTOR)
                        .build();
                underTest.tell(retrieveThing, getRef());

                // A deleted Thing cannot be retrieved anymore.
                expectMsgClass(ThingNotAccessibleException.class);

                // re-create the thing
                underTest.tell(createThing, getRef());

                final CreateThingResponse reCreateThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(reCreateThingResponse.getThingCreated().orElse(null), thing, 3);

                final Event expectedReCreatedEvent = toEvent(createThing, 3);
                assertJournal(thingId, Arrays.asList(expectedDeletedEvent, expectedReCreatedEvent));
                assertSnapshots(thingId, Collections.singletonList(expectedDeletedSnapshot));

                // retrieve the re-created thing
                underTest.tell(retrieveThing, getRef());
                final RetrieveThingResponse retrieveThingAfterRestartResponse = expectMsgClass(RetrieveThingResponse
                        .class);
                assertThingInResponse(retrieveThingAfterRestartResponse.getThing(), thing, 3);
            }
        };
    }

    /**
     * In case that a deleted thing has been already been snapshotted and another snapshot was triggered by
     * the activity-check-handler, the latest snapshot was deleted.
     */
    @Test
    public void snapshotOfDeletedThingIsNotDeletedWhenAlreadySnapshotAndTheActivityIntervalPassed() {
        final int activityCheckDeletedIntervalSecs = 2;
        final Config customConfig = createNewDefaultTestConfig().
                withValue(ConfigKeys.Thing.ACTIVITY_CHECK_DELETED_INTERVAL, ConfigValueFactory.fromAnyRef(Duration
                        .ofSeconds(activityCheckDeletedIntervalSecs)));
        setup(customConfig);

        new TestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final String thingId = thing.getId().orElseThrow(IllegalStateException::new);

                // use a supervisor actor, otherwise we could reuse the actorSystem in this test because the
                // thingsPersistenceActor will stop its parent, leading to the actorSystem being terminated
                ActorRef underTest = createSupervisorActorFor(thingId);
                watch(underTest);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                final Thing thingCreated = createThingResponse.getThingCreated()
                        .orElseThrow(IllegalStateException::new);
                assertThingInResponse(thingCreated, thing, 1);

                final Event expectedCreatedEvent = toEvent(createThing, 1);
                assertJournal(thingId, Collections.singletonList(expectedCreatedEvent));
                assertSnapshotsEmpty(thingId);

                final DeleteThing deleteThing = DeleteThing.of(thingId, dittoHeadersV2);
                underTest.tell(deleteThing, getRef());
                expectMsgEquals(DeleteThingResponse.of(thingId, dittoHeadersV2));

                final Thing expectedDeletedSnapshot = toDeletedThing(thingCreated, 2);
                assertSnapshots(thingId, Collections.singletonList(expectedDeletedSnapshot));
                final Event expectedDeletedEvent = toEvent(deleteThing, 2);
                // created-event has been deleted due to snapshot
                assertJournal(thingId, Collections.singletonList(expectedDeletedEvent));

                // wait for the actor to be terminated due to the end of the activity interval
                expectTerminated(FiniteDuration.apply(activityCheckDeletedIntervalSecs + 5, TimeUnit.SECONDS),
                        underTest);

                underTest = Retry.untilSuccess(() -> createSupervisorActorFor(thingId));

                final RetrieveThing retrieveThing = RetrieveThing.getBuilder(thingId, dittoHeadersV2)
                        .withSelectedFields(FIELD_SELECTOR)
                        .build();
                underTest.tell(retrieveThing, getRef());

                // A deleted Thing cannot be retrieved anymore.
                expectMsgClass(ThingNotAccessibleException.class);

                // re-create the thing
                underTest.tell(createThing, getRef());

                final CreateThingResponse reCreateThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(reCreateThingResponse.getThingCreated().orElse(null), thing, 3);

                final Event expectedReCreatedEvent = toEvent(createThing, 3);
                assertJournal(thingId, Arrays.asList(expectedDeletedEvent, expectedReCreatedEvent));
                assertSnapshots(thingId, Collections.singletonList(expectedDeletedSnapshot));

                // retrieve the re-created thing
                underTest.tell(retrieveThing, getRef());
                final RetrieveThingResponse retrieveThingAfterRestartResponse = expectMsgClass(RetrieveThingResponse
                        .class);
                assertThingInResponse(retrieveThingAfterRestartResponse.getThing(), thing, 3);
            }
        };
    }

    /**
     * Checks that the snapshots (in general) contain the expected revision no and data. Before the bugfix,
     * things sometimes were snapshot with incorrect data (from previous version).
     */
    @Test
    public void thingInArbitraryStateIsSnapshotCorrectly() {
        setup(createNewDefaultTestConfig());

        new TestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final String thingId = thing.getId().orElseThrow(IllegalStateException::new);

                ActorRef underTest = createPersistenceActorFor(thingId);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                assertThingInResponse(createThingResponse.getThingCreated().orElse(null), thing, 1);

                final Event expectedCreatedEvent = toEvent(createThing, 1);
                assertJournal(thingId, Collections.singletonList(expectedCreatedEvent));
                assertSnapshotsEmpty(thingId);

                final Thing thingForModify = ThingsModelFactory.newThingBuilder(thing)
                        .setAttribute(JsonFactory.newPointer("/foo"), JsonValue.of("bar"))
                        .setRevision(2)
                        .build();
                final ModifyThing modifyThing = ModifyThing.of(thingId, thingForModify, null, dittoHeadersV2);
                underTest.tell(modifyThing, getRef());

                final ModifyThingResponse modifyThingResponse = expectMsgClass(ModifyThingResponse.class);
                ThingCommandAssertions.assertThat(modifyThingResponse).hasStatus(HttpStatusCode.NO_CONTENT);

                final Event expectedModifiedEvent = toEvent(modifyThing, 2);
                // created-event has been deleted due to snapshot
                assertJournal(thingId, Collections.singletonList(expectedModifiedEvent));
                assertSnapshots(thingId, Collections.singletonList(thingForModify));

                // Make sure that the actor has the correct revision no of 2
                final RetrieveThing retrieveThing = RetrieveThing.getBuilder(thingId, dittoHeadersV2)
                        .withSelectedFields(FIELD_SELECTOR)
                        .build();
                underTest.tell(retrieveThing, getRef());

                final RetrieveThingResponse retrieveThingResponse = expectMsgClass(RetrieveThingResponse.class);
                assertThingInResponse(retrieveThingResponse.getThing(), thingForModify, 2);

                // restart actor to recover thing state: make sure that the revision is still 2 (will be loaded from
                // snapshot)
                watch(underTest);
                underTest.tell(PoisonPill.getInstance(), getRef());
                expectTerminated(underTest);
                underTest = Retry.untilSuccess(() -> createPersistenceActorFor(thingId));

                underTest.tell(retrieveThing, getRef());

                final RetrieveThingResponse retrieveThingAfterRestartResponse =
                        expectMsgClass(RetrieveThingResponse.class);
                assertThingInResponse(retrieveThingAfterRestartResponse.getThing(), thingForModify, 2);
            }
        };
    }

    /**
     * Checks that the old snapshot is not deleted when configuration property
     * {@link ConfigKeys.Thing#SNAPSHOT_DELETE_OLD} is {@code false}.
     */
    @Test
    public void oldSnapshotIsNotDeletedWhenSnapshotDeleteOldIsFalse() {
        final Config customConfig = createNewDefaultTestConfig().
                withValue(ConfigKeys.Thing.SNAPSHOT_DELETE_OLD, ConfigValueFactory.fromAnyRef(false)).
                withValue(ConfigKeys.Thing.SNAPSHOT_THRESHOLD, ConfigValueFactory.fromAnyRef(1));
        setup(customConfig);

        new TestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final String thingId = thing.getId().orElseThrow(IllegalStateException::new);

                final ActorRef underTest = createPersistenceActorFor(thingId);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                final Thing createdThing = createThingResponse.getThingCreated().orElse(null);
                assertThingInResponse(createdThing, thing, 1);

                final Event expectedCreatedEvent = toEvent(createThing, 1);
                assertJournal(thingId, Collections.singletonList(expectedCreatedEvent));
                assertSnapshots(thingId, Collections.singletonList(createdThing));

                final Thing thingForModify1 = ThingsModelFactory.newThingBuilder(thing)
                        .setAttribute(JsonFactory.newPointer("/foo"), JsonValue.of("bar1"))
                        .setRevision(2)
                        .build();
                final ModifyThing modifyThing1 = ModifyThing.of(thingId, thingForModify1, null, dittoHeadersV2);
                underTest.tell(modifyThing1, getRef());

                final ModifyThingResponse modifyThingResponse1 = expectMsgClass(ModifyThingResponse.class);
                ThingCommandAssertions.assertThat(modifyThingResponse1).hasStatus(HttpStatusCode.NO_CONTENT);

                final Event expectedModifiedEvent1 = toEvent(modifyThing1, 2);
                assertJournal(thingId, Arrays.asList(expectedCreatedEvent, expectedModifiedEvent1));
                // no snapshot has been made, because new snapshot (2) - latest snapshot (1) is NOT > threshold (1)
                assertSnapshots(thingId, Collections.singletonList(createdThing));

                final Thing thingForModify2 = ThingsModelFactory.newThingBuilder(thing).setAttribute(JsonFactory
                        .newPointer("/foo"), JsonValue.of("bar2")).setRevision(3).build();
                final ModifyThing modifyThing2 = ModifyThing.of(thingId, thingForModify2, null, dittoHeadersV2);
                underTest.tell(modifyThing2, getRef());

                final ModifyThingResponse modifyThingResponse2 = expectMsgClass(ModifyThingResponse.class);
                ThingCommandAssertions.assertThat(modifyThingResponse2).hasStatus(HttpStatusCode.NO_CONTENT);

                final Event expectedModifiedEvent2 = toEvent(modifyThing2, 3);
                // as expected: because snapshot has been made, old events have been deleted
                assertJournal(thingId, Collections.singletonList(expectedModifiedEvent2));
                /* snapshot has been made, because new snapshot (3) - latest snapshot (1) is > threshold (1)
                   and - as expected - the old snapshot has not been deleted.
                 */
                assertSnapshots(thingId, Arrays.asList(createdThing, thingForModify2));
            }
        };
    }

    /**
     * Checks that old events are not deleted when configuration property
     * {@link ConfigKeys.Thing#EVENTS_DELETE_OLD} is {@code false}.
     */
    @Test
    public void oldEventsAreNotDeletedWhenEventsDeleteOldIsFalse() {
        final Config customConfig = createNewDefaultTestConfig().
                withValue(ConfigKeys.Thing.EVENTS_DELETE_OLD, ConfigValueFactory.fromAnyRef(false)).
                withValue(ConfigKeys.Thing.SNAPSHOT_THRESHOLD, ConfigValueFactory.fromAnyRef(1));
        setup(customConfig);

        new TestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final String thingId = thing.getId().orElseThrow(IllegalStateException::new);

                final ActorRef underTest = createPersistenceActorFor(thingId);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                final Thing createdThing = createThingResponse.getThingCreated().orElse(null);
                assertThingInResponse(createdThing, thing, 1);

                final Event expectedCreatedEvent = toEvent(createThing, 1);
                assertJournal(thingId, Collections.singletonList(expectedCreatedEvent));
                assertSnapshots(thingId, Collections.singletonList(createdThing));

                final Thing thingForModify1 = ThingsModelFactory.newThingBuilder(thing)
                        .setAttribute(JsonFactory.newPointer("/foo"), JsonValue.of("bar1"))
                        .setRevision(2)
                        .build();
                final ModifyThing modifyThing1 = ModifyThing.of(thingId, thingForModify1, null, dittoHeadersV2);
                underTest.tell(modifyThing1, getRef());

                final ModifyThingResponse modifyThingResponse1 = expectMsgClass(ModifyThingResponse.class);
                ThingCommandAssertions.assertThat(modifyThingResponse1).hasStatus(HttpStatusCode.NO_CONTENT);

                final Event expectedModifiedEvent1 = toEvent(modifyThing1, 2);
                assertJournal(thingId, Arrays.asList(expectedCreatedEvent, expectedModifiedEvent1));
                // no snapshot has been made, because new snapshot (2) - latest snapshot (1) is NOT > threshold (1)
                assertSnapshots(thingId, Collections.singletonList(createdThing));

                final Thing thingForModify2 = ThingsModelFactory.newThingBuilder(thing).setAttribute(JsonFactory
                        .newPointer("/foo"), JsonValue.of("bar2")).setRevision(3).build();
                final ModifyThing modifyThing2 = ModifyThing.of(thingId, thingForModify2, null, dittoHeadersV2);
                underTest.tell(modifyThing2, getRef());

                final ModifyThingResponse modifyThingResponse2 = expectMsgClass(ModifyThingResponse.class);
                ThingCommandAssertions.assertThat(modifyThingResponse2).hasStatus(HttpStatusCode.NO_CONTENT);

                final Event expectedModifiedEvent2 = toEvent(modifyThing2, 3);
                // as expected: despite snapshot has been made, no events have been deleted
                assertJournal(thingId, Arrays.asList(expectedCreatedEvent, expectedModifiedEvent1,
                        expectedModifiedEvent2));
                /* snapshot has been made, because new snapshot (3) - latest snapshot (1) is > threshold (1)
                   and - as expected - the old snapshot has been deleted.
                 */
                assertSnapshots(thingId, Collections.singletonList(thingForModify2));
            }
        };
    }

    /**
     * Checks that a snapshot is generated after the snapshot interval has passed, if there were changes to the
     * document.
     */
    @Test
    public void snapshotIsCreatedAfterSnapshotIntervalHasPassed() {
        final int snapshotIntervalSecs = 5;
        final Config customConfig = createNewDefaultTestConfig().
                withValue(ConfigKeys.Thing.SNAPSHOT_THRESHOLD, ConfigValueFactory.fromAnyRef(Long.MAX_VALUE)).
                withValue(ConfigKeys.Thing.SNAPSHOT_INTERVAL,
                        ConfigValueFactory.fromAnyRef(Duration.ofSeconds(snapshotIntervalSecs)));
        setup(customConfig);

        new TestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final String thingId = thing.getId().orElseThrow(IllegalStateException::new);

                final ActorRef underTest = createPersistenceActorFor(thingId);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                final Thing createdThing = createThingResponse.getThingCreated()
                        .orElseThrow(IllegalStateException::new);
                assertThingInResponse(createdThing, thing, 1);

                final Event expectedCreatedEvent = toEvent(createThing, 1);
                assertJournal(thingId, Collections.singletonList(expectedCreatedEvent));
                // snapshots are empty, because the snapshot-interval has not yet passed
                assertSnapshotsEmpty(thingId);

                // wait until snapshot-interval has passed
                waitSecs(snapshotIntervalSecs);
                assertJournal(thingId, Collections.singletonList(expectedCreatedEvent));
                // snapshot has been created
                assertSnapshots(thingId, Collections.singletonList(createdThing));

                final Thing thingForModify = ThingsModelFactory.newThingBuilder(thing)
                        .setAttribute(JsonFactory.newPointer("/foo"), JsonValue.of("bar"))
                        .setRevision(2)
                        .build();
                final ModifyThing modifyThing = ModifyThing.of(thingId, thingForModify, null, dittoHeadersV2);
                underTest.tell(modifyThing, getRef());

                final ModifyThingResponse modifyThingResponse1 = expectMsgClass(ModifyThingResponse.class);
                ThingCommandAssertions.assertThat(modifyThingResponse1).hasStatus(HttpStatusCode.NO_CONTENT);

                final Event expectedModifiedEvent1 = toEvent(modifyThing, 2);
                assertJournal(thingId, Arrays.asList(expectedCreatedEvent, expectedModifiedEvent1));
                // snapshot has not yet been made, because the snapshot-interval has not yet passed
                assertSnapshots(thingId, Collections.singletonList(createdThing));

                // wait again until snapshot-interval has passed
                waitSecs(snapshotIntervalSecs);
                // because snapshot has been created, the "old" created-event has been deleted
                assertJournal(thingId, Collections.singletonList(expectedModifiedEvent1));
                // snapshot has been created and old snapshot has been deleted
                assertSnapshots(thingId, Collections.singletonList(thingForModify));
            }
        };
    }

    /** */
    @Test
    public void snapshotsAreNotCreatedTwiceIfSnapshotHasBeenAlreadyBeenCreatedDueToThresholdAndSnapshotIntervalHasPassed() {
        final int snapshotIntervalMillisecs = 3;
        final Config customConfig = createNewDefaultTestConfig().
                withValue(ConfigKeys.Thing.SNAPSHOT_INTERVAL,
                        ConfigValueFactory.fromAnyRef(Duration.ofSeconds(snapshotIntervalMillisecs)));
        setup(customConfig);

        new TestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final String thingId = thing.getId()
                        .orElseThrow(IllegalStateException::new);

                final ActorRef underTest = createPersistenceActorFor(thingId);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                final Thing createdThing = createThingResponse.getThingCreated()
                        .orElseThrow(IllegalStateException::new);
                assertThingInResponse(createdThing, thing, 1);

                final Event expectedCreatedEvent = toEvent(createThing, 1);
                assertJournal(thingId, Collections.singletonList(expectedCreatedEvent));
                assertSnapshotsEmpty(thingId);

                final Thing thingForModify = ThingsModelFactory.newThingBuilder(thing)
                        .setAttribute(JsonFactory.newPointer("/foo"), JsonValue.of("bar"))
                        .setRevision(2)
                        .build();
                final ModifyThing modifyThing = ModifyThing.of(thingId, thingForModify, null, dittoHeadersV2);
                underTest.tell(modifyThing, getRef());

                final ModifyThingResponse modifyThingResponse1 = expectMsgClass(ModifyThingResponse.class);
                ThingCommandAssertions.assertThat(modifyThingResponse1).hasStatus(HttpStatusCode.NO_CONTENT);

                final Event expectedModifiedEvent1 = toEvent(modifyThing, 2);
                assertJournal(thingId, Collections.singletonList(expectedModifiedEvent1));
                assertSnapshots(thingId, Collections.singletonList(thingForModify));

                // wait until snapshot-interval has passed
                waitSecs(snapshotIntervalMillisecs);
                // there must have no snapshot been added
                assertSnapshots(thingId, Collections.singletonList(thingForModify));
            }
        };
    }

    @Test
    public void lastSnapshotIsNotDeletedIfProtected() {
        final Config customConfig = createNewDefaultTestConfig()
                .withValue(ConfigKeys.Thing.SNAPSHOT_THRESHOLD,
                        ConfigValueFactory.fromAnyRef(NEVER_TAKE_SNAPSHOT_THRESHOLD));
        setup(customConfig);

        new TestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final String thingId = thing.getId().orElseThrow(IllegalStateException::new);

                final ActorRef underTest = createPersistenceActorFor(thingId);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                final Thing createdThing = createThingResponse.getThingCreated()
                        .orElseThrow(IllegalStateException::new);
                assertThingInResponse(createdThing, thing, 1);

                final Event expectedCreatedEvent = toEvent(createThing, 1);
                assertJournal(thingId, Collections.singletonList(expectedCreatedEvent));
                assertSnapshotsEmpty(thingId);

                final Thing thingForModify = ThingsModelFactory.newThingBuilder(thing)
                        .setAttribute(JsonFactory.newPointer("/foo"), JsonValue.of("bar"))
                        .setRevision(2)
                        .build();
                final ModifyThing modifyThing = ModifyThing.of(thingId, thingForModify, null, dittoHeadersV2);
                underTest.tell(modifyThing, getRef());

                final ModifyThingResponse modifyThingResponse1 = expectMsgClass(ModifyThingResponse.class);
                ThingCommandAssertions.assertThat(modifyThingResponse1).hasStatus(HttpStatusCode.NO_CONTENT);

                underTest.tell(TagThing.of(thingId, dittoHeadersV2), getRef());
                final TagThingResponse tagThingResponse = expectMsgClass(TagThingResponse.class);
                assertThat(tagThingResponse).isEqualTo(TagThingResponse.of(thingId,2, dittoHeadersV2));
                assertSnapshots(thingId, Collections.singletonList(thingForModify));

                final Thing thingForModify2 = ThingsModelFactory.newThingBuilder(thing)
                        .setAttribute(JsonFactory.newPointer("/foo"), JsonValue.of("bar2"))
                        .setRevision(3)
                        .build();

                final ModifyThing modifyThing2 = ModifyThing.of(thingId, thingForModify2, null, dittoHeadersV2);
                underTest.tell(modifyThing2, getRef());

                final ModifyThingResponse modifyThingResponse2 = expectMsgClass(ModifyThingResponse.class);
                ThingCommandAssertions.assertThat(modifyThingResponse2).hasStatus(HttpStatusCode.NO_CONTENT);

                underTest.tell(TagThing.of(thingId, dittoHeadersV2), getRef());
                final TagThingResponse tagThingResponse2 = expectMsgClass(TagThingResponse.class);
                assertThat(tagThingResponse2).isEqualTo(TagThingResponse.of(thingId, 3, dittoHeadersV2));

                assertSnapshots(thingId, Arrays.asList(thingForModify, thingForModify2));
            }
        };
    }

    @Test
    public void retrieveThingWithSnapshotRevision() {
        final Config customConfig = createNewDefaultTestConfig().withValue(ConfigKeys.Thing.SNAPSHOT_THRESHOLD,
                ConfigValueFactory.fromAnyRef(NEVER_TAKE_SNAPSHOT_THRESHOLD));
        setup(customConfig);

        new TestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final String thingId = thing.getId().orElseThrow(IllegalStateException::new);

                final ActorRef underTest = createPersistenceActorFor(thingId);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                final Thing createdThing = createThingResponse.getThingCreated()
                        .orElseThrow(IllegalStateException::new);
                assertThingInResponse(createdThing, thing, 1);

                final Event expectedCreatedEvent = toEvent(createThing, 1);
                assertJournal(thingId, Collections.singletonList(expectedCreatedEvent));
                assertSnapshotsEmpty(thingId);

                final Thing thingForModify = ThingsModelFactory.newThingBuilder(thing)
                        .setAttribute(JsonFactory.newPointer("/foo"), JsonValue.of("bar"))
                        .setRevision(2)
                        .build();
                final ModifyThing modifyThing = ModifyThing.of(thingId, thingForModify, null, dittoHeadersV2);
                underTest.tell(modifyThing, getRef());

                final ModifyThingResponse modifyThingResponse1 = expectMsgClass(ModifyThingResponse.class);
                ThingCommandAssertions.assertThat(modifyThingResponse1).hasStatus(HttpStatusCode.NO_CONTENT);

                underTest.tell(TagThing.of(thingId, dittoHeadersV2), getRef());
                final TagThingResponse tagThingResponse = expectMsgClass(TagThingResponse.class);
                assertThat(tagThingResponse).isEqualTo(TagThingResponse.of(thingId, 2, dittoHeadersV2));
                assertSnapshots(thingId, Collections.singletonList(thingForModify));
                final long revision = tagThingResponse.getSnapshotRevision();

                final Thing thingForModify2 = ThingsModelFactory.newThingBuilder(thing)
                        .setAttribute(JsonFactory.newPointer("/foo"), JsonValue.of("bar2"))
                        .setRevision(3)
                        .build();

                final ModifyThing modifyThing2 = ModifyThing.of(thingId, thingForModify2, null, dittoHeadersV2);
                underTest.tell(modifyThing2, getRef());

                final ModifyThingResponse modifyThingResponse2 = expectMsgClass(ModifyThingResponse.class);
                ThingCommandAssertions.assertThat(modifyThingResponse2).hasStatus(HttpStatusCode.NO_CONTENT);


                underTest.tell(TagThing.of(thingId, dittoHeadersV2), getRef());
                final TagThingResponse tagThingResponse2 = expectMsgClass(TagThingResponse.class);
                assertThat(tagThingResponse2).isEqualTo(TagThingResponse.of(thingId, 3, dittoHeadersV2));
                assertSnapshots(thingId, Arrays.asList(thingForModify, thingForModify2));

                final RetrieveThing retrieveThing = RetrieveThing.getBuilder(thingId, dittoHeadersV2).build();

                underTest.tell(retrieveThing, getRef());
                final RetrieveThingResponse retrieveThingResponse = expectMsgClass(RetrieveThingResponse.class);
                assertThat(getAttributeValue(retrieveThingResponse, "/foo")).isEqualTo("bar2");

                final RetrieveThing retrieveThingWithSnapshot = RetrieveThing.getBuilder(thingId, dittoHeadersV2)
                        .withSnapshotRevision(revision)
                        .build();

                underTest.tell(retrieveThingWithSnapshot, getRef());
                // set duration higher than the default timeout of 3 seconds, because it is too low for
                // retrieving the snapshot from the persistence
                final RetrieveThingResponse retrieveThingWithSnapshotResponse = expectMsgClass(
                        new FiniteDuration(5, TimeUnit.SECONDS),
                        RetrieveThingResponse.class);
                assertThat(getAttributeValue(retrieveThingWithSnapshotResponse, "/foo")).isEqualTo("bar");

            }

            private String getAttributeValue(final RetrieveThingResponse retrieveThingResponse, final String key) {
                return retrieveThingResponse.getThing().getAttributes().get().getValue(key).get().asString();
            }
        };
    }

    /** */
    @Test
    public void actorCannotBeStartedWithNegativeSnapshotThreshold() {
        final Config customConfig = createNewDefaultTestConfig().withValue(ConfigKeys.Thing.SNAPSHOT_THRESHOLD,
                ConfigValueFactory.fromAnyRef(-1));
        setup(customConfig);

        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createPersistenceActorFor("fail");
                watch(underTest);
                expectTerminated(underTest);
            }
        };
    }

    private static void assertThingInSnapshot(final Thing actualThing, final Thing expectedThing) {
        assertThingInResponse(actualThing, expectedThing, expectedThing.getRevision().map(ThingRevision::toLong)
                .orElseThrow(IllegalArgumentException::new));
    }

    private static void assertThingInJournal(final Thing actualThing, final Thing expectedThing) {
        final Thing expectedComparisonThing = ThingsModelFactory.newThingBuilder(expectedThing)
                .build();

        DittoThingsAssertions.assertThat(actualThing)
                .hasEqualJson(expectedComparisonThing, FIELD_SELECTOR, IS_MODIFIED.negate())
                .hasNoModified();
    }

    private static void assertThingInResponse(final Thing actualThing, final Thing expectedThing,
            final long expectedRevision) {
        final Thing expectedComparisonThing = ThingsModelFactory.newThingBuilder(expectedThing)
                .setRevision(expectedRevision)
                .build();

        DittoThingsAssertions.assertThat(actualThing)
                .hasEqualJson(expectedComparisonThing, FIELD_SELECTOR, IS_MODIFIED.negate())
                .isModified(); // we cannot check exact timestamp
    }

    private void assertSnapshotsEmpty(final String thingId) {
        assertSnapshots(thingId, Collections.emptyList());
    }

    private void assertJournal(final String thingId, final List<Event> expectedEvents) {
        retryOnAssertionError(() -> {
            final List<ThingEvent> actualEvents = journalTestHelper.getAllEvents(thingId);

            Assertions.assertListWithIndexInfo(actualEvents, (actual, expected) -> {
                assertThat(actual)
                        .hasType(expected.getType())
                        .hasRevision(expected.getRevision());

                if (actual instanceof ThingModified) {
                    assertThingInJournal(((ThingModified) actual).getThing(), ((ThingModified) expected).getThing());
                } else if (actual instanceof ThingCreated) {
                    assertThingInJournal(((ThingCreated) actual).getThing(), ((ThingCreated) expected).getThing());
                } else if (actual instanceof ThingDeleted) {
                    // no special check
                    assertTrue(true);
                } else {
                    throw new UnsupportedOperationException("No check for: " + actual.getClass());
                }
            }).isEqualTo(expectedEvents);
        });
    }

    private static Thing toDeletedThing(final Thing thing, final int newRevision) {
        return thing.toBuilder().setRevision(newRevision).setLifecycle(ThingLifecycle.DELETED).build();
    }

    private Event toEvent(final Command command, final long revision) {
        final Class<? extends Command> clazz = command.getClass();
        final BiFunction<Command, Long, ThingEvent> commandToEventFunction = commandToEventMapperRegistry.get(clazz);
        if (commandToEventFunction == null) {
            throw new UnsupportedOperationException("Mapping not yet implemented for type: " + clazz);
        }

        return commandToEventFunction.apply(command, revision);
    }

    private void assertSnapshots(final String thingId, final List<Thing> expectedSnapshots) {
        retryOnAssertionError(() -> {
            final List<Thing> snapshots = snapshotTestHelper.getAllSnapshotsAscending(thingId);
            Assertions.assertListWithIndexInfo(snapshots, ThingPersistenceActorSnapshottingTest::assertThingInSnapshot)
                    .isEqualTo(expectedSnapshots);
        });
    }

    private static void retryOnAssertionError(final Runnable r) {
        Assertions.retryOnAssertionError(r, PERSISTENCE_ASSERT_WAIT_AT_MOST_MS, PERSISTENCE_ASSERT_RETRY_DELAY_MS);
    }

    private ThingEvent convertJournalEntryToEvent(final DBObject dbObject, final long sequenceNumber) {
        final ThingEvent<?> head = (ThingEvent) eventAdapter.fromJournal(dbObject, null).events().head();
        return head.setRevision(sequenceNumber);
    }

    private static Thing convertSnapshotDataToThing(final DBObject dbObject, final long sequenceNumber) {
        final DittoBsonJson dittoBsonJson = DittoBsonJson.getInstance();
        final JsonObject json = dittoBsonJson.serialize(dbObject).asObject();

        final Thing thing = ThingsModelFactory.newThing(json);

        DittoThingsAssertions.assertThat(thing).hasRevision(ThingRevision.newInstance(sequenceNumber));

        return thing;
    }

    private static String convertDomainIdToPersistenceId(final String domainId) {
        return ThingPersistenceActor.PERSISTENCE_ID_PREFIX + domainId;
    }

}
