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
import java.util.Arrays;
import java.util.Collections;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.base.model.common.HttpStatus;
import org.eclipse.ditto.base.model.signals.events.EventsourcedEvent;
import org.eclipse.ditto.internal.utils.config.DittoConfigError;
import org.eclipse.ditto.internal.utils.test.Retry;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.json.JsonFactory;
import org.eclipse.ditto.json.JsonFieldSelector;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.things.model.Thing;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.ThingsModelFactory;
import org.eclipse.ditto.things.model.signals.commands.assertions.ThingCommandAssertions;
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThing;
import org.eclipse.ditto.things.model.signals.commands.modify.CreateThingResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteThing;
import org.eclipse.ditto.things.model.signals.commands.modify.DeleteThingResponse;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThing;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyThingResponse;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThingResponse;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueFactory;

import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.testkit.javadsl.TestKit;

/**
 * Unit test for the snapshotting functionality of {@link ThingPersistenceActor}.
 */
public final class ThingPersistenceActorSnapshottingTest extends PersistenceActorTestBaseWithSnapshotting {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    private static final Logger LOGGER = LoggerFactory.getLogger(ThingPersistenceActorSnapshottingTest.class);


    private static final JsonFieldSelector FIELD_SELECTOR = JsonFactory.newFieldSelector(Thing.JsonFields.ATTRIBUTES,
            Thing.JsonFields.FEATURES, Thing.JsonFields.ID, Thing.JsonFields.MODIFIED, Thing.JsonFields.REVISION,
            Thing.JsonFields.POLICY_ID, Thing.JsonFields.LIFECYCLE);

    @Rule
    public final TestWatcher watchman = new TestedMethodLoggingWatcher(LOGGER);

    /**
     * Check that a deleted thing is snapshot correctly and can be recreated. Before the bugfix, the
     * deleted thing was snapshot with incorrect data (previous version), thus it would be handled as created
     * after actor restart.
     */
    @Test
    public void deletedThingIsSnapshotWithCorrectDataAndCanBeRecreated() {
        setup(testConfig);

        new TestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final ThingId thingId = thing.getEntityId().orElseThrow(IllegalStateException::new);

                ActorRef underTest = createPersistenceActorFor(thingId);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                final CreateThingResponse createThingResponse = expectMsgClass(CreateThingResponse.class);
                final Thing thingCreated = createThingResponse.getThingCreated()
                        .orElseThrow(IllegalStateException::new);
                assertThingInResponse(thingCreated, thing, 1);

                final EventsourcedEvent<?> expectedCreatedEvent = toEvent(createThing, thingCreated);
                assertJournal(thingId, Collections.singletonList(expectedCreatedEvent));
                assertSnapshotsEmpty(thingId);

                final DeleteThing deleteThing = DeleteThing.of(thingId, dittoHeadersV2);
                underTest.tell(deleteThing, getRef());
                expectMsgEquals(DeleteThingResponse.of(thingId, dittoHeadersV2));

                final Thing expectedDeletedSnapshot = toDeletedThing(2);
                assertSnapshots(thingId, Collections.singletonList(expectedDeletedSnapshot));
                final EventsourcedEvent<?> expectedDeletedEvent = toEvent(deleteThing, expectedDeletedSnapshot);
                // created-event has been deleted due to snapshot
                assertJournal(thingId, Arrays.asList(expectedCreatedEvent, expectedDeletedEvent));

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
                final Thing actualThing = reCreateThingResponse.getThingCreated().orElse(null);
                assertThingInResponse(actualThing, thing, 3);

                final EventsourcedEvent<?> expectedReCreatedEvent = toEvent(createThing, actualThing);
                assertJournal(thingId,
                        Arrays.asList(expectedCreatedEvent, expectedDeletedEvent, expectedReCreatedEvent));
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
        setup(testConfig);

        new TestKit(actorSystem) {
            {
                final Thing thing = createThingV2WithRandomId();
                final ThingId thingId = thing.getEntityId().orElseThrow(IllegalStateException::new);

                ActorRef underTest = createPersistenceActorFor(thingId);

                final CreateThing createThing = CreateThing.of(thing, null, dittoHeadersV2);
                underTest.tell(createThing, getRef());

                LOGGER.info("Told CreateThing, expecting CreateThingResponse ...");

                final CreateThingResponse createThingResponse =
                        expectMsgClass(Duration.ofSeconds(10L), CreateThingResponse.class);
                final Thing actualThing = createThingResponse.getThingCreated().orElse(null);
                assertThingInResponse(actualThing, thing, 1);

                LOGGER.info("Expecting Event made it to Journal and snapshots are empty. ..");

                final EventsourcedEvent<?> expectedCreatedEvent = toEvent(createThing, actualThing);
                assertJournal(thingId, Collections.singletonList(expectedCreatedEvent));
                assertSnapshotsEmpty(thingId);

                final Thing thingForModify = ThingsModelFactory.newThingBuilder(thing)
                        .setAttribute(JsonFactory.newPointer("/foo"), JsonValue.of("bar"))
                        .setRevision(2)
                        .build();
                final ModifyThing modifyThing = ModifyThing.of(thingId, thingForModify, null, dittoHeadersV2);
                underTest.tell(modifyThing, getRef());

                LOGGER.info("Told ModifyThing, expecting ModifyThingResponse. ..");

                final ModifyThingResponse modifyThingResponse = expectMsgClass(ModifyThingResponse.class);
                ThingCommandAssertions.assertThat(modifyThingResponse).hasStatus(HttpStatus.NO_CONTENT);

                LOGGER.info("Expecting Event made it to Journal and snapshots contain Thing..");

                final EventsourcedEvent<?> expectedModifiedEvent = toEvent(modifyThing, thingForModify);
                assertJournal(thingId, Arrays.asList(expectedCreatedEvent, expectedModifiedEvent));
                assertSnapshots(thingId, Collections.singletonList(thingForModify));

                // Make sure that the actor has the correct revision no of 2
                final RetrieveThing retrieveThing = RetrieveThing.getBuilder(thingId, dittoHeadersV2)
                        .withSelectedFields(FIELD_SELECTOR)
                        .build();
                underTest.tell(retrieveThing, getRef());

                LOGGER.info("Told RetrieveThing, expecting RetrieveThingResponse ...");

                final RetrieveThingResponse retrieveThingResponse = expectMsgClass(RetrieveThingResponse.class);
                assertThingInResponse(retrieveThingResponse.getThing(), thingForModify, 2);

                LOGGER.info("Restarting ThingPersistenceActor ...");

                // restart actor to recover thing state: make sure that the revision is still 2 (will be loaded from
                // snapshot)
                watch(underTest);
                underTest.tell(PoisonPill.getInstance(), getRef());

                LOGGER.info("Expecting ThingPersistenceActor to be terminated ...");

                expectTerminated(underTest);

                LOGGER.info("Recreating ThingPersistenceActor ...");

                underTest = Retry.untilSuccess(() -> createPersistenceActorFor(thingId));

                underTest.tell(retrieveThing, getRef());

                LOGGER.info("Told RetrieveThing, expecting RetrieveThingResponse..");

                final RetrieveThingResponse retrieveThingAfterRestartResponse =
                        expectMsgClass(RetrieveThingResponse.class);
                assertThingInResponse(retrieveThingAfterRestartResponse.getThing(), thingForModify, 2);
            }
        };
    }

    @Test
    public void actorCannotBeStartedWithNegativeSnapshotThreshold() {
        final Config customConfig = createNewDefaultTestConfig().withValue(SNAPSHOT_THRESHOLD,
                ConfigValueFactory.fromAnyRef(-1));
        Assertions.assertThatExceptionOfType(DittoConfigError.class)
                        .isThrownBy(() -> setup(customConfig));
    }
}
