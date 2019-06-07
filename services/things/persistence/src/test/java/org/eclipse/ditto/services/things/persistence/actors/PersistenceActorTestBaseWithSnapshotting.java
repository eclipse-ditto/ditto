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

import static org.eclipse.ditto.signals.events.things.assertions.ThingEventAssertions.assertThat;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.bson.BsonDocument;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
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
import org.eclipse.ditto.signals.commands.base.Command;
import org.eclipse.ditto.signals.commands.things.modify.CreateThing;
import org.eclipse.ditto.signals.commands.things.modify.DeleteThing;
import org.eclipse.ditto.signals.commands.things.modify.ModifyThing;
import org.eclipse.ditto.signals.events.base.Event;
import org.eclipse.ditto.signals.events.things.ThingCreated;
import org.eclipse.ditto.signals.events.things.ThingDeleted;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.eclipse.ditto.signals.events.things.ThingModified;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import akka.actor.ExtendedActorSystem;

/**
 * Base test class for testing snapshotting of persistence actors.
 */
public abstract class PersistenceActorTestBaseWithSnapshotting extends PersistenceActorTestBase {

    static final JsonFieldSelector FIELD_SELECTOR = JsonFactory.newFieldSelector(Thing.JsonFields.ATTRIBUTES,
            Thing.JsonFields.FEATURES, Thing.JsonFields.ID, Thing.JsonFields.MODIFIED, Thing.JsonFields.REVISION,
            Thing.JsonFields.POLICY_ID, Thing.JsonFields.LIFECYCLE);
    static final int DEFAULT_TEST_SNAPSHOT_THRESHOLD = 2;
    private static final int NEVER_TAKE_SNAPSHOT_THRESHOLD = Integer.MAX_VALUE;
    private static final boolean DEFAULT_TEST_SNAPSHOT_DELETE_OLD = true;
    private static final boolean DEFAULT_TEST_EVENTS_DELETE_OLD = true;
    private static final Duration VERY_LONG_DURATION = Duration.ofDays(100);
    private static final int PERSISTENCE_ASSERT_WAIT_AT_MOST_MS = 5000;
    private static final long PERSISTENCE_ASSERT_RETRY_DELAY_MS = 500;

    private ThingMongoEventAdapter eventAdapter;
    private ThingsJournalTestHelper<ThingEvent> journalTestHelper;
    private ThingsSnapshotTestHelper<Thing> snapshotTestHelper;

    private Map<Class<? extends Command>, BiFunction<Command, Long, ThingEvent>> commandToEventMapperRegistry;

    Config createNewDefaultTestConfig() {
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

    @Override
    protected void setup(final Config customConfig) {
        super.setup(customConfig);
        eventAdapter = new ThingMongoEventAdapter((ExtendedActorSystem) actorSystem);
        journalTestHelper = new ThingsJournalTestHelper<>(actorSystem, this::convertJournalEntryToEvent,
                PersistenceActorTestBaseWithSnapshotting::convertDomainIdToPersistenceId);
        snapshotTestHelper = new ThingsSnapshotTestHelper<>(actorSystem,
                PersistenceActorTestBaseWithSnapshotting::convertSnapshotDataToThing,
                PersistenceActorTestBaseWithSnapshotting::convertDomainIdToPersistenceId);

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

    static void assertThingInResponse(final Thing actualThing, final Thing expectedThing,
            final long expectedRevision) {
        final Thing expectedComparisonThing = ThingsModelFactory.newThingBuilder(expectedThing)
                .setRevision(expectedRevision)
                .build();

        DittoThingsAssertions.assertThat(actualThing)
                .hasEqualJson(expectedComparisonThing, FIELD_SELECTOR, IS_MODIFIED.negate())
                .isModified(); // we cannot check exact timestamp
    }

    void assertSnapshotsEmpty(final String thingId) {
        assertSnapshots(thingId, Collections.emptyList());
    }

    void assertJournal(final String thingId, final List<Event> expectedEvents) {
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

    static Thing toDeletedThing(final Thing thing, final int newRevision) {
        return thing.toBuilder().setRevision(newRevision).setLifecycle(ThingLifecycle.DELETED).build();
    }

    Event toEvent(final Command command, final long revision) {
        final Class<? extends Command> clazz = command.getClass();
        final BiFunction<Command, Long, ThingEvent> commandToEventFunction = commandToEventMapperRegistry.get(clazz);
        if (commandToEventFunction == null) {
            throw new UnsupportedOperationException("Mapping not yet implemented for type: " + clazz);
        }

        return commandToEventFunction.apply(command, revision);
    }

    void assertSnapshots(final String thingId, final List<Thing> expectedSnapshots) {
        retryOnAssertionError(() -> {
            final List<Thing> snapshots = snapshotTestHelper.getAllSnapshotsAscending(thingId);
            Assertions.assertListWithIndexInfo(snapshots,
                    PersistenceActorTestBaseWithSnapshotting::assertThingInSnapshot)
                    .isEqualTo(expectedSnapshots);
        });
    }

    private static void retryOnAssertionError(final Runnable r) {
        Assertions.retryOnAssertionError(r, PERSISTENCE_ASSERT_WAIT_AT_MOST_MS, PERSISTENCE_ASSERT_RETRY_DELAY_MS);
    }

    private ThingEvent convertJournalEntryToEvent(final BsonDocument dbObject, final long sequenceNumber) {
        final ThingEvent<?> head = (ThingEvent) eventAdapter.fromJournal(dbObject, null).events().head();
        return head.setRevision(sequenceNumber);
    }

    private static Thing convertSnapshotDataToThing(final BsonDocument dbObject, final long sequenceNumber) {
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
