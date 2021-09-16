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

import static org.eclipse.ditto.things.model.signals.events.assertions.ThingEventAssertions.assertThat;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.bson.BsonDocument;
import org.eclipse.ditto.base.model.entity.id.EntityId;
import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.signals.commands.Command;
import org.eclipse.ditto.base.model.signals.events.AbstractEventsourcedEvent;
import org.eclipse.ditto.base.model.signals.events.EventsourcedEvent;
import org.eclipse.ditto.internal.utils.persistence.mongo.DittoBsonJson;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingBuilder;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingLifecycle;
import org.eclipse.ditto.things.model.ThingRevision;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.assertions.DittoThingsAssertions;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThing;
import org.eclipse.ditto.things.model.signals.events.ThingCreated;
import org.eclipse.ditto.things.model.signals.events.ThingDeleted;
import org.eclipse.ditto.things.model.signals.events.ThingEvent;
import org.eclipse.ditto.things.model.signals.events.ThingModified;
import org.eclipse.ditto.things.service.persistence.serializer.ThingMongoEventAdapter;
import org.eclipse.ditto.things.service.persistence.testhelper.Assertions;
import org.eclipse.ditto.things.service.persistence.testhelper.ThingsJournalTestHelper;
import org.eclipse.ditto.things.service.persistence.testhelper.ThingsSnapshotTestHelper;

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
    private static final Duration VERY_LONG_DURATION = Duration.ofDays(100);
    private static final int PERSISTENCE_ASSERT_WAIT_AT_MOST_MS = 5000;
    private static final long PERSISTENCE_ASSERT_RETRY_DELAY_MS = 500;

    private static final String SNAPSHOT_PREFIX = "ditto.things.thing.snapshot.";
    static final String SNAPSHOT_THRESHOLD = SNAPSHOT_PREFIX + "threshold";
    private static final String SNAPSHOT_INTERVAL = SNAPSHOT_PREFIX + "interval";
    private static final String ACTIVITY_CHECK_PREFIX = "ditto.things.thing.activity-check";
    private static final String ACTIVITY_CHECK_INTERVAL = ACTIVITY_CHECK_PREFIX + "inactive-interval";
    private static final String ACTIVITY_CHECK_DELETED_INTERVAL = ACTIVITY_CHECK_PREFIX + "deleted-interval";

    private ThingMongoEventAdapter eventAdapter;
    private ThingsJournalTestHelper<ThingEvent<?>> journalTestHelper;
    private ThingsSnapshotTestHelper<Thing> snapshotTestHelper;

    private Map<Class<? extends Command<?>>, BiFunction<Command<?>, Thing, EventsourcedEvent<?>>>
            commandToEventMapperRegistry;

    Config createNewDefaultTestConfig() {
        return ConfigFactory.empty()
                .withValue(SNAPSHOT_THRESHOLD, ConfigValueFactory.fromAnyRef(
                        DEFAULT_TEST_SNAPSHOT_THRESHOLD))
                .withValue(ACTIVITY_CHECK_INTERVAL, ConfigValueFactory.fromAnyRef(VERY_LONG_DURATION))
                .withValue(ACTIVITY_CHECK_DELETED_INTERVAL,
                        ConfigValueFactory.fromAnyRef(VERY_LONG_DURATION))
                .withValue(SNAPSHOT_INTERVAL, ConfigValueFactory.fromAnyRef(VERY_LONG_DURATION));
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
        commandToEventMapperRegistry.put(CreateThing.class, (command, thing) -> {
            final CreateThing createCommand = (CreateThing) command;
            final ThingBuilder.FromCopy thingBuilder = createCommand.getThing().toBuilder();
            thing.getCreated().ifPresent(thingBuilder::setCreated);
            thing.getModified().ifPresent(thingBuilder::setModified);
            thing.getRevision().ifPresent(thingBuilder::setRevision);
            return ThingCreated.of(thingBuilder.build(),
                    thing.getRevision().get().toLong(),
                    thing.getModified().orElse(null),
                    DittoHeaders.empty(),
                    thing.getMetadata().orElse(null));
        });
        commandToEventMapperRegistry.put(ModifyThing.class, (command, thing) -> {
            final ModifyThing modifyCommand = (ModifyThing) command;
            final ThingBuilder.FromCopy thingBuilder = modifyCommand.getThing().toBuilder();
            thing.getCreated().ifPresent(thingBuilder::setCreated);
            thing.getModified().ifPresent(thingBuilder::setModified);
            thing.getRevision().ifPresent(thingBuilder::setRevision);
            return ThingModified.of(thingBuilder.build(),
                    thing.getRevision().get().toLong(),
                    thing.getModified().orElse(null),
                    DittoHeaders.empty(),
                    thing.getMetadata().orElse(null));
        });
        commandToEventMapperRegistry.put(DeleteThing.class, (command, thing) -> {
            final DeleteThing deleteCommand = (DeleteThing) command;
            return ThingDeleted.of(deleteCommand.getEntityId(),
                    thing.getRevision().orElseThrow().toLong(),
                    thing.getModified().orElse(null),
                    DittoHeaders.empty(),
                    thing.getMetadata().orElse(null));
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
                .hasEqualJson(expectedComparisonThing, FIELD_SELECTOR, IS_MODIFIED.negate());
    }

    static void assertThingInResponse(final Thing actualThing, final Thing expectedThing,
            final long expectedRevision) {
        final Thing expectedComparisonThing = ThingsModelFactory.newThingBuilder(expectedThing)
                .setRevision(expectedRevision)
                .build();

        DittoThingsAssertions.assertThat(actualThing)
                .hasEqualJson(expectedComparisonThing, FIELD_SELECTOR, IS_MODIFIED.negate());
    }

    void assertSnapshotsEmpty(final ThingId thingId) {
        assertSnapshots(thingId, Collections.emptyList());
    }

    void assertJournal(final ThingId thingId, final List<EventsourcedEvent<?>> expectedEvents) {
        retryOnAssertionError(() -> {
            final List<ThingEvent<?>> actualEvents = journalTestHelper.getAllEvents(thingId);
            Assertions.assertListWithIndexInfo(actualEvents, (actual, expected) -> {
                assertThat(actual)
                        .hasType(expected.getType());

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

    static Thing toDeletedThing(final int newRevision) {
        return ThingsModelFactory.newThingBuilder()
                .setRevision(newRevision)
                .setLifecycle(ThingLifecycle.DELETED)
                .build();
    }

    EventsourcedEvent<?> toEvent(final Command<?> command, final Thing templateThing) {
        final BiFunction<Command<?>, Thing, EventsourcedEvent<?>> commandToEventFunction =
                commandToEventMapperRegistry.get(command.getClass());
        if (commandToEventFunction == null) {
            throw new UnsupportedOperationException("Mapping not yet implemented for type: " + command.getClass());
        }

        return commandToEventFunction.apply(command, templateThing);
    }

    void assertSnapshots(final ThingId thingId, final List<Thing> expectedSnapshots) {
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

    private ThingEvent<?> convertJournalEntryToEvent(final BsonDocument dbObject, final long sequenceNumber) {
        final AbstractEventsourcedEvent<?> head = (AbstractEventsourcedEvent<?>)
                eventAdapter.fromJournal(dbObject, null).events().head();
        return (ThingEvent<?>) head.setRevision(sequenceNumber);
    }

    private static Thing convertSnapshotDataToThing(final BsonDocument dbObject, final long sequenceNumber) {
        final DittoBsonJson dittoBsonJson = DittoBsonJson.getInstance();
        final JsonObject json = dittoBsonJson.serialize(dbObject).asObject();

        final Thing thing = ThingsModelFactory.newThing(json);

        DittoThingsAssertions.assertThat(thing).hasRevision(ThingRevision.newInstance(sequenceNumber));

        return thing;
    }

    private static String convertDomainIdToPersistenceId(final EntityId domainId) {
        return ThingPersistenceActor.PERSISTENCE_ID_PREFIX + domainId;
    }

}
