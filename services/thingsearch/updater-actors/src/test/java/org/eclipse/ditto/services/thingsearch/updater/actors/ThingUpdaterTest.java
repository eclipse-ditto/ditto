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
package org.eclipse.ditto.services.thingsearch.updater.actors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.eclipse.ditto.json.JsonFactory.newPointer;
import static org.eclipse.ditto.json.JsonFactory.newValue;
import static org.eclipse.ditto.model.base.assertions.DittoBaseAssertions.assertThat;
import static org.eclipse.ditto.model.base.json.JsonSchemaVersion.V_1;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.common.HttpStatusCode;
import org.eclipse.ditto.model.base.exceptions.DittoRuntimeException;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.base.json.JsonSchemaVersion;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AccessControlListModelFactory;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.Feature;
import org.eclipse.ditto.model.things.Permission;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingBuilder;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.services.models.things.ThingTag;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.services.thingsearch.persistence.write.ThingMetadata;
import org.eclipse.ditto.services.thingsearch.persistence.write.ThingsSearchUpdaterPersistence;
import org.eclipse.ditto.services.utils.akka.JavaTestProbe;
import org.eclipse.ditto.services.utils.akka.streaming.StreamAck;
import org.eclipse.ditto.signals.base.ShardedMessageEnvelope;
import org.eclipse.ditto.signals.events.policies.PolicyDeleted;
import org.eclipse.ditto.signals.events.policies.PolicyModified;
import org.eclipse.ditto.signals.events.things.AttributeCreated;
import org.eclipse.ditto.signals.events.things.FeatureModified;
import org.eclipse.ditto.signals.events.things.ThingCreated;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.eclipse.ditto.signals.events.things.ThingModified;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.pattern.CircuitBreaker;
import akka.stream.javadsl.Source;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

/**
 * Unit test for {@link ThingUpdater}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class ThingUpdaterTest {

    private static final String POLICY_ID = "abc:policy";
    private static final long INITIAL_POLICY_REVISION = -1L;
    private static final String THING_ID = "abc:myId";

    private static final long REVISION = 1L;

    private static final AuthorizationSubject AUTHORIZATION_SUBJECT = AuthorizationSubject.newInstance("sid");

    private static final AclEntry ACL_ENTRY =
            AclEntry.newInstance(AUTHORIZATION_SUBJECT, AccessControlListModelFactory.allPermissions());

    private static final AccessControlList ACL = AccessControlListModelFactory.newAcl(ACL_ENTRY);

    private final Thing thing = ThingsModelFactory.newThingBuilder().setId(THING_ID).setRevision(REVISION).build();

    // how many ms to wait for a mockito call
    private static final int MOCKITO_TIMEOUT = 2500;

    private ActorSystem actorSystem;

    @Mock
    private ThingsSearchUpdaterPersistence persistenceMock;

    private Source<Boolean, NotUsed> successWithDelay() {
        final int sourceDelayMillis = 1500;
        return Source.single(Boolean.TRUE).initialDelay(Duration.create(sourceDelayMillis, MILLISECONDS));
    }

    /** */
    @Before
    public void setUpBase() {
        final Config config = ConfigFactory.load("test");
        startActorSystem(config);
        when(persistenceMock.getThingMetadata(any())).thenReturn(
                Source.single(new ThingMetadata(-1L, null, INITIAL_POLICY_REVISION)));
        when(persistenceMock.insertOrUpdate(any(), anyLong(), anyLong())).thenReturn(Source.single(true));
        when(persistenceMock.executeCombinedWrites(any(), any(), any(), anyLong())).thenReturn(Source.single(true));
    }

    /** */
    @After
    public void tearDownBase() {
        shutdownActorSystem();
    }

    private void startActorSystem(final Config config) {
        shutdownActorSystem();
        actorSystem = ActorSystem.create("AkkaTestSystem", config);
    }

    private void shutdownActorSystem() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
            actorSystem = null;
        }
    }

    @Test
    public void createThing() {
        // disable policy load
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        new TestKit(actorSystem) {
            {
                final Thing thingWithAcl =
                        thing.setAclEntry(AclEntry.newInstance(AuthorizationSubject.newInstance("user1"),
                                Permission.READ)).toBuilder().setRevision(1L).build();

                final ActorRef underTest = createInitializedThingUpdaterActor();

                final ThingEvent thingCreated = ThingCreated.of(thingWithAcl, 1L, dittoHeaders);
                underTest.tell(thingCreated, getRef());

                waitUntil().insertOrUpdate(eq(thingWithAcl), eq(1L), anyLong());
            }
        };
    }

    @Test
    public void unknownThingEventTriggersResync() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().schemaVersion(V_1).build();

        new TestKit(actorSystem) {
            {
                final TestProbe thingsShardProbe = TestProbe.apply(actorSystem);
                final TestProbe policiesShardProbe = TestProbe.apply(actorSystem);
                final ActorRef underTest = createInitializedThingUpdaterActor(thingsShardProbe, policiesShardProbe);

                waitUntil().insertOrUpdate(any(Thing.class), anyLong(), anyLong());
                // now that ThingUpdater is in eventProcessing behavior we can try to insert unknown event
                final ThingEvent unknownEvent = Mockito.mock(ThingEvent.class);
                when(unknownEvent.getType()).thenReturn("unknownType");
                when(unknownEvent.getDittoHeaders()).thenReturn(dittoHeaders);
                when(unknownEvent.getRevision()).thenReturn(1L);
                underTest.tell(unknownEvent, getRef());

                // resync should be triggered
                expectShardedSudoRetrieveThing(thingsShardProbe, THING_ID);
            }
        };
    }

    @Test
    public void concurrentUpdates() {
        final Thing thingWithAcl = thing.setAclEntry(AclEntry.newInstance(AuthorizationSubject.newInstance("user1"),
                Permission.READ));
        assertEquals(V_1, thingWithAcl.getImplementedSchemaVersion());
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().schemaVersion(V_1).build();

        final ThingEvent attributeCreated0 =
                AttributeCreated.of(THING_ID, newPointer("p0"), newValue(true), 2L, dittoHeaders);
        final ThingEvent attributeCreated1 =
                AttributeCreated.of(THING_ID, newPointer("p1"), newValue(true), 3L, dittoHeaders);
        final ThingEvent attributeCreated2 =
                AttributeCreated.of(THING_ID, newPointer("p2"), newValue(true), 4L, dittoHeaders);
        final ThingEvent attributeCreated3 =
                AttributeCreated.of(THING_ID, newPointer("p3"), newValue(true), 5L, dittoHeaders);

        final List<ThingEvent> expectedWrites1 =
                Collections.singletonList(attributeCreated0);
        final List<ThingEvent> expectedWrites2 = new ArrayList<>();
        expectedWrites2.add(attributeCreated1);
        expectedWrites2.add(attributeCreated2);
        expectedWrites2.add(attributeCreated3);


        when(persistenceMock.executeCombinedWrites(eq(THING_ID), eq(expectedWrites1),
                any(), anyLong())).thenReturn
                (successWithDelay());

        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createInitializedThingUpdaterActor();

                final ThingEvent thingCreated = ThingCreated.of(thingWithAcl, 1L, dittoHeaders);

                //This processing will cause that the mocked mongoDB operation to last for 500 ms
                underTest.tell(thingCreated, getRef());
                waitUntil().insertOrUpdate(eq(thingWithAcl), eq(thingCreated.getRevision()), eq(-1L));


                underTest.tell(attributeCreated0, getRef());

                // the following events will be executed all together as they are processed while another operation is
                // in progress
                underTest.tell(attributeCreated1, getRef());
                underTest.tell(attributeCreated2, getRef());
                underTest.tell(attributeCreated3, getRef());

                waitUntil().executeCombinedWrites(eq(THING_ID), eq(expectedWrites1), any(),
                        eq(attributeCreated0.getRevision()));
                waitUntil().executeCombinedWrites(eq(THING_ID), eq(expectedWrites2), any(),
                        eq(attributeCreated3.getRevision()));
            }
        };
    }

    @Test
    public void unexpectedHighSequenceNumberTriggersSync() {
        final long eventRevision = 7L;
        final long thingRevision = 20L;

        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .schemaVersion(V_1) // for SudoRetrieveThingResponse to retain ACL
                .build();
        final ThingEvent attributeCreated =
                AttributeCreated.of(THING_ID, newPointer("p1"), newValue(true), eventRevision, dittoHeaders);
        final Thing currentThing = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setRevision(thingRevision)
                .setPermissions(ACL)
                .build();

        new JavaTestProbe(actorSystem) {
            {
                final TestProbe thingsShardProbe = TestProbe.apply(actorSystem);
                final TestProbe policiesShardProbe = TestProbe.apply(actorSystem);
                final ActorRef underTest = createInitializedThingUpdaterActor(thingsShardProbe, policiesShardProbe);
                // will cause that a sync is triggered
                underTest.tell(attributeCreated, ref());

                // wait until SudoRetrieveThing (inside a sharding envelope) is sent
                expectShardedSudoRetrieveThing(thingsShardProbe, THING_ID);

                underTest.tell(createSudoRetrieveThingsResponse(currentThing, dittoHeaders), ref());
                waitUntil().insertOrUpdate(eq(currentThing), eq(thingRevision), eq(-1L));
            }
        };
    }

    @Test
    public void unsuccessfulUpdateTriggersSync() {
        final long revision = 0L;
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .schemaVersion(V_1)
                .build();
        final ThingEvent attributeCreated =
                AttributeCreated.of(THING_ID, newPointer("p1"), newValue(true), 1L, dittoHeaders);
        final Thing currentThing = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setRevision(revision)
                .setPermissions(ACL)
                .build();
        final List<ThingEvent> expectedWrite =
                Collections.singletonList(attributeCreated);
        final Source<Boolean, NotUsed> sourceUnsuccess = Source.single(Boolean.FALSE);

        when(persistenceMock.executeCombinedWrites(eq(THING_ID), eq(expectedWrite), any(), anyLong())).thenReturn(
                sourceUnsuccess);

        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createInitializedThingUpdaterActor();
                // will cause that a sync is triggered
                underTest.tell(attributeCreated, getRef());
                waitUntil().executeCombinedWrites(eq(THING_ID), eq(expectedWrite), any(),
                        eq(attributeCreated.getRevision()));
                underTest.tell(createSudoRetrieveThingsResponse(currentThing, dittoHeaders), getRef());
                waitUntil().insertOrUpdate(eq(currentThing), eq(revision), eq(-1L));
            }
        };
    }

    @Test
    public void databaseExceptionTriggersSync() {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .schemaVersion(V_1)
                .build();
        final ThingEvent attributeCreated =
                AttributeCreated.of(THING_ID, newPointer("p1"), newValue(true), 1L, dittoHeaders);
        final List<ThingEvent> expectedWrites =
                Collections.singletonList(attributeCreated);
        when(persistenceMock.executeCombinedWrites(eq(THING_ID), eq(expectedWrites), any(), anyLong())).thenThrow(
                new RuntimeException("db operation mock error."));

        new JavaTestProbe(actorSystem) {
            {
                final TestProbe thingsShardProbe = TestProbe.apply(actorSystem);
                final TestProbe policiesShardProbe = TestProbe.apply(actorSystem);
                final ActorRef underTest = createInitializedThingUpdaterActor(thingsShardProbe, policiesShardProbe);
                // will cause that a sync is triggered
                underTest.tell(attributeCreated, ref());

                waitUntil().executeCombinedWrites(eq(THING_ID), eq(expectedWrites), any(),
                        eq(attributeCreated.getRevision()));

                // resync should be triggered
                expectShardedSudoRetrieveThing(thingsShardProbe, THING_ID);
            }
        };
    }

    @Test
    public void thingTagWithHigherSequenceNumberTriggersSync() {
        final long revision = 7L;
        final ThingTag thingTag = ThingTag.of(THING_ID, revision);
        final Thing currentThing = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setRevision(revision)
                .setPermissions(ACL)
                .build();
        final DittoHeaders emptyHeaders = DittoHeaders.empty();
        final DittoHeaders dittoHeadersV1 = DittoHeaders.newBuilder()
                .schemaVersion(V_1)
                .build();

        final JsonObject expectedSudoRetrieveThing =
                SudoRetrieveThing.withOriginalSchemaVersion(THING_ID, emptyHeaders).toJson();

        new TestKit(actorSystem) {
            {
                final TestProbe thingsShardProbe = TestProbe.apply(actorSystem);
                final TestProbe policiesShardProbe = TestProbe.apply(actorSystem);

                final ActorRef underTest = createInitializedThingUpdaterActor(thingsShardProbe, policiesShardProbe);
                // will cause that a sync is triggered
                underTest.tell(thingTag, ActorRef.noSender());
                final ShardedMessageEnvelope shardedMessageEnvelope =
                        thingsShardProbe.expectMsgClass(FiniteDuration.apply(5, SECONDS),
                                ShardedMessageEnvelope.class);
                assertEquals(expectedSudoRetrieveThing, shardedMessageEnvelope.getMessage());
                underTest.tell(createSudoRetrieveThingsResponse(currentThing, dittoHeadersV1), getRef());
                waitUntil().insertOrUpdate(eq(currentThing), eq(revision), eq(-1L));
            }
        };
    }

    @Test
    public void thingTagWithLowerSequenceNumberDoesNotTriggerSync() {
        new TestKit(actorSystem) {
            {
                final long revision = 1L;
                final Thing thingWithRevision = ThingsModelFactory.newThingBuilder()
                        .setId(THING_ID)
                        .setRevision(revision)
                        .setPermissions(ACL)
                        .build();

                final ThingTag thingTag = ThingTag.of(THING_ID, revision);
                final DittoHeaders dittoHeaders = DittoHeaders.empty();

                final TestProbe thingsShardProbe = TestProbe.apply(actorSystem);
                final TestProbe policiesShardProbe = TestProbe.apply(actorSystem);

                final ActorRef underTest = createInitializedThingUpdaterActor(thingsShardProbe, policiesShardProbe);
                final ThingEvent thingCreated = ThingCreated.of(thingWithRevision, revision, dittoHeaders);

                underTest.tell(thingCreated, getRef());
                waitUntil().insertOrUpdate(eq(thingWithRevision), eq(revision), eq(-1L));

                // should trigger no sync
                underTest.tell(thingTag, ActorRef.noSender());
                thingsShardProbe.expectNoMsg();
            }
        };
    }

    @Test
    public void thingEventWithEqualSequenceNumberDoesNotTriggerSync() {
        new TestKit(actorSystem) {
            {
                final long revision = 1L;
                final Thing thingWithRevision = ThingsModelFactory.newThingBuilder()
                        .setId(THING_ID)
                        .setRevision(revision)
                        .setPermissions(ACL)
                        .build();

                final DittoHeaders dittoHeaders = DittoHeaders.empty();

                final TestProbe thingsShardProbe = TestProbe.apply(actorSystem);
                final TestProbe policiesShardProbe = TestProbe.apply(actorSystem);

                final ActorRef underTest = createInitializedThingUpdaterActor(thingsShardProbe, policiesShardProbe);
                final ThingEvent thingCreated =
                        ThingCreated.of(thingWithRevision, revision, dittoHeaders);

                underTest.tell(thingCreated, getRef());
                waitUntil().insertOrUpdate(eq(thingWithRevision), eq(revision), eq(-1L));

                // should trigger no sync
                final ThingEvent thingModified =
                        ThingModified.of(thingWithRevision.toBuilder().setRevision(revision).build(),
                                revision, dittoHeaders);
                underTest.tell(thingModified, getRef());
                thingsShardProbe.expectNoMsg();
            }
        };
    }

    @Test
    public void persistenceErrorForCombinedWritesTriggersSync() {
        new TestKit(actorSystem) {
            {
                final long revision = 1L;
                final Thing thingWithRevision = ThingsModelFactory.newThingBuilder()
                        .setId(THING_ID)
                        .setRevision(revision)
                        .setPermissions(ACL)
                        .build();

                final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                        .schemaVersion(V_1)
                        .build();

                final TestProbe thingsShardProbe = TestProbe.apply(actorSystem);
                final TestProbe policiesShardProbe = TestProbe.apply(actorSystem);

                final ActorRef underTest = createInitializedThingUpdaterActor(thingsShardProbe, policiesShardProbe);
                final ThingEvent thingCreated =
                        ThingCreated.of(thingWithRevision, revision, dittoHeaders);

                underTest.tell(thingCreated, getRef());
                waitUntil().insertOrUpdate(eq(thingWithRevision), eq(revision), eq(-1L));

                when(persistenceMock.executeCombinedWrites(any(), any(), any(), anyLong()))
                        .thenThrow(new IllegalStateException("requiredByTest"));

                final AttributeCreated changeEvent =
                        AttributeCreated.of(THING_ID, JsonPointer.of("/foo"), JsonValue.of("bar"),
                                revision + 1, dittoHeaders);
                final List<ThingEvent> expectedWrites =
                        Collections.singletonList(changeEvent);
                underTest.tell(changeEvent, getRef());


                waitUntil().executeCombinedWrites(eq(THING_ID), eq(expectedWrites), eq(null),
                        eq(changeEvent.getRevision()));
                // should trigger sync
                expectShardedSudoRetrieveThing(thingsShardProbe, THING_ID);
            }
        };
    }

    @Test
    public void invalidThingEventTriggersSync() {
        new TestKit(actorSystem) {
            {
                final ThingEvent<?> invalidThingEvent = createInvalidThingEvent();

                final TestProbe thingsShardProbe = TestProbe.apply(actorSystem);
                final TestProbe policiesShardProbe = TestProbe.apply(actorSystem);

                final ActorRef underTest = createInitializedThingUpdaterActor(thingsShardProbe, policiesShardProbe);
                underTest.tell(invalidThingEvent, getRef());

                // should trigger sync
                expectShardedSudoRetrieveThing(thingsShardProbe, THING_ID);
            }
        };
    }

    @Test
    public void tooManyStashedMessagesDiscardOldMessages() {
        final Thing thingWithAcl = thing.setAclEntry(AclEntry.newInstance(AuthorizationSubject.newInstance("user1"),
                Permission.READ));
        // setup
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .schemaVersion(V_1)
                .build();

        final SudoRetrieveThing retrieveThing = SudoRetrieveThing.withOriginalSchemaVersion(THING_ID, dittoHeaders);

        final Thing currentThing = ThingsModelFactory.newThingBuilder(thingWithAcl)
                .setId(THING_ID)
                .setRevision(3L)
                .build();

        when(persistenceMock.getThingMetadata(any())).thenReturn(Source.single(new ThingMetadata(0L, null, -1L)));

        // set config with stash size of 3
        final Config config = ConfigFactory.load("test")
                .withValue("akka.actor.custom-updater-mailbox.stash-capacity", ConfigValueFactory.fromAnyRef(3));
        startActorSystem(config);

        new TestKit(actorSystem) {
            {
                final TestProbe thingsShardProbe = TestProbe.apply(actorSystem);
                final TestProbe policiesShardProbe = TestProbe.apply(actorSystem);
                final ActorRef underTest = createUninitializedThingUpdaterActor(thingsShardProbe.ref(),
                        policiesShardProbe.ref());

                // send a ThingCreated event and expect it to get persisted
                underTest.tell(ThingCreated.of(thingWithAcl, 1L, dittoHeaders), getRef());

                waitUntil().insertOrUpdate(eq(thingWithAcl), eq(1L), eq(-1L));

                // send a ThingTag with sequence number = 3, which is unexpected and the updater should trigger a sync
                underTest.tell(ThingTag.of(THING_ID, 3L), ActorRef.noSender());
                assertThat(expectShardedSudoRetrieveThing(thingsShardProbe, THING_ID).getMessage())
                        .isEqualToIgnoringFieldDefinitions(retrieveThing.toJson());

                // while the actor waits for the response of the Things service, we send 4 AttributeCreated events
                underTest.tell(AttributeCreated.of(THING_ID, newPointer("attr1"), newValue("value1"), 4L,
                        dittoHeaders), getRef());
                underTest.tell(AttributeCreated.of(THING_ID, newPointer("attr2"), newValue("value2"), 5L,
                        dittoHeaders), getRef());
                underTest.tell(AttributeCreated.of(THING_ID, newPointer("attr3"), newValue("value3"), 6L,
                        dittoHeaders), getRef());
                underTest.tell(AttributeCreated.of(THING_ID, newPointer("attr4"), newValue("value4"), 7L,
                        dittoHeaders), getRef());

                // we send the fake Thing service response, the updater actor is waiting for
                underTest.tell(createSudoRetrieveThingsResponse(currentThing, dittoHeaders), getRef());

                // the updater persists the thing with sequence number 3 and goes back to thing event processing
                waitUntil().insertOrUpdate(eq(currentThing), eq(3L), eq(-1L));

                // because we send 4 AttributeCreated events, but the stash capacity is only 3, the event with the expected
                // sequence number 4 was dropped and we should receive the AttributeCreated event with sequence number 5
                // instead. this triggers another sync as expected
                assertThat(expectShardedSudoRetrieveThing(thingsShardProbe, THING_ID).getMessage())
                        .isEqualToIgnoringFieldDefinitions(retrieveThing.toJson());
            }
        };
    }

    /** */
    @Test
    public void policyEventTriggersPolicyUpdate() {
        final long policyRevision = REVISION;
        final long newPolicyRevision = 2L;
        final Policy initialPolicy = Policy.newBuilder(THING_ID)
                .setRevision(policyRevision)
                .build();
        final Policy policy = Policy.newBuilder(THING_ID)
                .setRevision(newPolicyRevision)
                .setGrantedPermissionsFor(TestConstants.Policy.SUBJECT_ID, TestConstants.Policy.RESOURCE_KEY,
                        TestConstants.Policy.PERMISSION_READ)
                .build();
        final DittoHeaders emptyDittoHeaders = DittoHeaders.empty();
        final PolicyModified policyEvent = PolicyModified.of(policy, newPolicyRevision, emptyDittoHeaders);
        final DittoHeaders retrievePolicyDittoHeaders = DittoHeaders.empty();

        final Thing thingWithPolicyId = thing.setPolicyId(THING_ID);

        final SudoRetrievePolicyResponse initialPolicyResponse =
                SudoRetrievePolicyResponse.of(THING_ID, initialPolicy, retrievePolicyDittoHeaders);
        final SudoRetrievePolicyResponse sudoRetrievePolicyResponse =
                SudoRetrievePolicyResponse.of(THING_ID, policy, retrievePolicyDittoHeaders);
        final SudoRetrieveThingResponse sudoRetrieveThingResponse =
                createSudoRetrieveThingsResponse(thingWithPolicyId, emptyDittoHeaders);

        new TestKit(actorSystem) {
            {
                final TestProbe thingsShardProbe = TestProbe.apply(actorSystem);
                final TestProbe policiesShardProbe = TestProbe.apply(actorSystem);
                final ActorRef underTest = createInitializedThingUpdaterActor(thingsShardProbe, policiesShardProbe);

                refreshPolicyUpdateAnswers(REVISION, THING_ID, policyRevision);

                // establish policy ID
                underTest.tell(ThingCreated.of(thingWithPolicyId, 1L, emptyDittoHeaders), getRef());
                policiesShardProbe.expectMsgClass(SudoRetrievePolicy.class);
                underTest.tell(initialPolicyResponse, null);

                // wait until the Thing is indexed
                waitUntil().insertOrUpdate(any(), anyLong(), eq(policyRevision));

                underTest.tell(policyEvent, getRef());

                // request current thing
                expectShardedSudoRetrieveThing(thingsShardProbe, THING_ID);
                underTest.tell(sudoRetrieveThingResponse, null);

                // request current policy
                final SudoRetrievePolicy sudoRetrievePolicy =
                        policiesShardProbe.expectMsgClass(SudoRetrievePolicy.class);
                assertEquals(THING_ID, sudoRetrievePolicy.getId());
                underTest.tell(sudoRetrievePolicyResponse, null);

                waitUntil().updatePolicy(eq(thingWithPolicyId), any(Enforcer.class));
            }
        };
    }

    @Test
    public void policyIdChangeTriggersSync() {
        final String policy1Id = "policy:1";
        final String policy2Id = "policy:2";
        final Policy policy1 = Policy.newBuilder(policy1Id).setRevision(REVISION).build();
        final Policy policy2 = Policy.newBuilder(policy2Id).setRevision(REVISION).build();

        final Thing thingWithPolicy1 = ThingsModelFactory.newThingBuilder(thing)
                .setRevision(1L)
                .setPolicyId(policy1Id)
                .build();
        final Thing thingWithPolicy2 = ThingsModelFactory.newThingBuilder(thing)
                .setRevision(2L)
                .setPolicyId(policy2Id)
                .build();
        final SudoRetrievePolicyResponse policyResponse1 =
                SudoRetrievePolicyResponse.of(policy1Id, policy1, DittoHeaders.empty());
        final SudoRetrievePolicyResponse policyResponse2 =
                SudoRetrievePolicyResponse.of(policy2Id, policy2, DittoHeaders.empty());
        final ThingCreated thingCreated = ThingCreated.of(thingWithPolicy1, 1L, DittoHeaders.empty());
        final ThingModified thingModified = ThingModified.of(thingWithPolicy2, 2L, DittoHeaders.empty());

        new TestKit(actorSystem) {
            {
                final TestProbe thingsShardProbe = TestProbe.apply(actorSystem);
                final TestProbe policiesShardProbe = TestProbe.apply(actorSystem);
                final ActorRef underTest = createInitializedThingUpdaterActor(thingsShardProbe, policiesShardProbe);

                // establish policy ID
                refreshPolicyUpdateAnswers(0L, policy1Id, REVISION);
                underTest.tell(thingCreated, getRef());

                final SudoRetrievePolicy sudoRetrievePolicy =
                        policiesShardProbe.expectMsgClass(SudoRetrievePolicy.class);
                assertEquals(policy1Id, sudoRetrievePolicy.getId());
                underTest.tell(policyResponse1, null);

                // wait until the Thing is indexed
                waitUntil().insertOrUpdate(any(), anyLong(), anyLong());
                waitUntil().updatePolicy(any(), any());

                refreshPolicyUpdateAnswers(1L, policy2Id, REVISION);
                underTest.tell(thingModified, getRef());

                // request current policy
                final SudoRetrievePolicy sudoRetrievePolicy2 =
                        policiesShardProbe.expectMsgClass(SudoRetrievePolicy.class);
                assertEquals(policy2Id, sudoRetrievePolicy2.getId());
                underTest.tell(policyResponse2, null);

                waitUntil().updatePolicy(eq(thingWithPolicy2), any(Enforcer.class));
            }
        };
    }

    @Test
    public void thingV1BecomesThingV2() {
        final String policyId = "policy:2";
        final Policy policy = Policy.newBuilder(policyId).setRevision(REVISION).build();

        final Thing thingWithoutPolicy = ThingsModelFactory.newThingBuilder(thing)
                .setRevision(1L)
                .setPermissions(ACL)
                .build();
        final Thing thingWithPolicy = ThingsModelFactory.newThingBuilder(thing)
                .setRevision(2L)
                .setPolicyId(policyId)
                .build();
        final SudoRetrievePolicyResponse policyResponse =
                SudoRetrievePolicyResponse.of(policyId, policy, DittoHeaders.empty());
        final ThingCreated thingCreated = ThingCreated.of(thingWithoutPolicy, 1L, DittoHeaders.empty());
        final ThingModified thingModified = ThingModified.of(thingWithPolicy, 2L, DittoHeaders.empty());

        new TestKit(actorSystem) {
            {
                final TestProbe thingsShardProbe = TestProbe.apply(actorSystem);
                final TestProbe policiesShardProbe = TestProbe.apply(actorSystem);
                final ActorRef underTest = createInitializedThingUpdaterActor(thingsShardProbe, policiesShardProbe);

                // establish policy ID
                underTest.tell(thingCreated, getRef());

                // wait until the Thing is indexed
                waitUntil().insertOrUpdate(eq(thingWithoutPolicy), eq(1L), eq(-1L));

                underTest.tell(thingModified, getRef());

                // request current policy
                final SudoRetrievePolicy sudoRetrievePolicy =
                        policiesShardProbe.expectMsgClass(SudoRetrievePolicy.class);
                assertEquals(policyId, sudoRetrievePolicy.getId());
                underTest.tell(policyResponse, null);

                waitUntil().updatePolicy(eq(thingWithPolicy), any(Enforcer.class));
            }
        };
    }

    @Test
    public void thingTagTriggersSyncDuringInit() {
        new TestKit(actorSystem) {{
            final TestProbe thingsProbe = TestProbe.apply(actorSystem);
            final ActorRef dummy = TestProbe.apply(actorSystem).ref();
            final ActorRef underTest = createUninitializedThingUpdaterActor(thingsProbe.ref(), dummy);

            // WHEN: updater receives ThingTag with a bigger sequence number during initialization
            ThingTag message = ThingTag.of(THING_ID, 1L);
            underTest.tell(message, ActorRef.noSender());

            // THEN: sync is triggered
            expectShardedSudoRetrieveThing(thingsProbe, THING_ID);
        }};
    }

    @Test
    public void thingEventWithoutThingTriggersSyncDuringInit() {
        new TestKit(actorSystem) {{
            final TestProbe thingsProbe = TestProbe.apply(actorSystem);
            final ActorRef dummy = TestProbe.apply(actorSystem).ref();
            final ActorRef underTest = createUninitializedThingUpdaterActor(thingsProbe.ref(), dummy);

            // WHEN: updater receives ThingEvent not containing a Thing
            final Feature feature = Feature.newBuilder().withId("thingEventWithoutThingTriggersSyncDuringInit").build();
            final Object message = FeatureModified.of(THING_ID, feature, 0L, DittoHeaders.empty());
            underTest.tell(message, null);

            // THEN: sync is triggered
            expectShardedSudoRetrieveThing(thingsProbe, THING_ID);
            verify(persistenceMock, never()).executeCombinedWrites(anyString(), anyList(), eq(null), anyLong());
        }};
    }

    @Test
    public void thingEventWithThingCauseUpdateDuringInit() {
        new TestKit(actorSystem) {{
            final TestProbe thingsProbe = TestProbe.apply(actorSystem);
            final ActorRef dummy = TestProbe.apply(actorSystem).ref();
            final ActorRef underTest = createUninitializedThingUpdaterActor(thingsProbe.ref(), dummy);

            // WHEN: updater receives ThingEvent containing a Thing
            final Thing thingWithAcl = thing.setAclEntry(
                    AclEntry.newInstance(AuthorizationSubject.newInstance("thingEventWithThingCauseUpdateDuringInit"),
                            Permission.READ)).toBuilder().setRevision(1L).build();
            final Object message = ThingModified.of(thingWithAcl, 1L, DittoHeaders.empty());
            underTest.tell(message, null);

            // THEN: write-operation is requested from the persistence
            waitUntil().insertOrUpdate(eq(thingWithAcl), eq(1L), anyLong());
        }};

    }

    @Test
    public void policyEventTriggersSyncDuringInit() {
        new TestKit(actorSystem) {{
            // GIVEN: updater initialized with policy ID
            Mockito.reset(persistenceMock);
            when(persistenceMock.getThingMetadata(any())).thenReturn(
                    Source.single(new ThingMetadata(1L, POLICY_ID, 1L)));
            final TestProbe thingsProbe = TestProbe.apply(actorSystem);
            final ActorRef dummy = TestProbe.apply(actorSystem).ref();
            final ActorRef underTest = createUninitializedThingUpdaterActor(thingsProbe.ref(), dummy);

            // WHEN: updater receives ThingEvent not containing a Thing
            final Object message = PolicyDeleted.of(POLICY_ID, 2L, DittoHeaders.empty());
            underTest.tell(message, null);

            // THEN: sync is triggered
            expectShardedSudoRetrieveThing(thingsProbe, THING_ID);
        }};
    }

    @Test
    public void acknowledgesSuccessfulSync() {
        final long thingTagRevision = 7L;
        final long thingRevision = 20L;

        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .schemaVersion(V_1) // for SudoRetrieveThingResponse to retain ACL
                .build();
        final Thing currentThing = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setRevision(thingRevision)
                .setPermissions(ACL)
                .build();

        new JavaTestProbe(actorSystem) {
            {
                final TestProbe thingsShardProbe = TestProbe.apply(actorSystem);
                final TestProbe policiesShardProbe = TestProbe.apply(actorSystem);
                final ActorRef underTest = createInitializedThingUpdaterActor(thingsShardProbe, policiesShardProbe);

                // GIVEN: a ThingTag with nonempty sender triggers synchronization
                final ThingTag thingTag = ThingTag.of(THING_ID, thingTagRevision);
                underTest.tell(thingTag, ref());

                // WHEN: synchronization is successful
                expectShardedSudoRetrieveThing(thingsShardProbe, THING_ID);
                underTest.tell(createSudoRetrieveThingsResponse(currentThing, dittoHeaders), ref());
                waitUntil().insertOrUpdate(eq(currentThing), eq(thingRevision), eq(-1L));

                // THEN: success is acknowledged
                expectMsgEquals(StreamAck.success(thingTag.asIdentifierString()));
            }
        };
    }

    @Test
    public void acknowledgesFailedSync() {
        final long thingTagRevision = 7L;

        new JavaTestProbe(actorSystem) {
            {
                final TestProbe thingsShardProbe = TestProbe.apply(actorSystem);
                final TestProbe policiesShardProbe = TestProbe.apply(actorSystem);
                final ActorRef underTest = createInitializedThingUpdaterActor(thingsShardProbe, policiesShardProbe);

                // GIVEN: a ThingTag with nonempty sender triggers synchronization
                final ThingTag thingTag = ThingTag.of(THING_ID, thingTagRevision);
                underTest.tell(thingTag, ref());

                // WHEN: synchronization is unsuccessful, thing updater will retry two times
                final HttpStatusCode teapot = HttpStatusCode.IM_A_TEAPOT;
                expectShardedSudoRetrieveThing(thingsShardProbe, THING_ID);
                underTest.tell(DittoRuntimeException.newBuilder("dummy 1", teapot).build(), ActorRef.noSender());
                expectShardedSudoRetrieveThing(thingsShardProbe, THING_ID);
                underTest.tell(DittoRuntimeException.newBuilder("dummy 2", teapot).build(), ActorRef.noSender());
                expectShardedSudoRetrieveThing(thingsShardProbe, THING_ID);
                underTest.tell(DittoRuntimeException.newBuilder("dummy 3", teapot).build(), ActorRef.noSender());

                // THEN: failure is acknowledged
                expectMsgEquals(StreamAck.failure(thingTag.asIdentifierString()));
            }
        };
    }

    @Test
    public void acknowledgesSkippedSync() {
        final long thingTagRevision = -1L;

        new JavaTestProbe(actorSystem) {
            {
                final TestProbe thingsShardProbe = TestProbe.apply(actorSystem);
                final TestProbe policiesShardProbe = TestProbe.apply(actorSystem);
                final ActorRef underTest = createInitializedThingUpdaterActor(thingsShardProbe, policiesShardProbe);

                // GIVEN: a ThingTag with nonempty sender does not trigger synchronization
                final ThingTag thingTag = ThingTag.of(THING_ID, thingTagRevision);
                underTest.tell(thingTag, ref());

                // THEN: success is acknowledged
                expectMsgEquals(StreamAck.success(thingTag.asIdentifierString()));
            }
        };
    }

    private void expectRetrievePolicyAndAnswer(
            final Policy policy,
            final TestProbe policiesActor,
            final ActorRef thingsUpdater,
            final java.time.Duration timeout,
            final DittoHeaders headers) {
        final SudoRetrievePolicy retrievePolicy = policiesActor.expectMsgClass(
                toScala(orDefaultTimeout(timeout)),
                SudoRetrievePolicy.class);
        assertThat(retrievePolicy.getId()).isEqualTo(policy.getId().orElseThrow(IllegalStateException::new));

        thingsUpdater.tell(
                SudoRetrievePolicyResponse.of(policy.getId().orElseThrow(IllegalStateException::new), policy, headers),
                policiesActor.ref());
    }

    private ActorRef createInitializedThingUpdaterActor() {
        return createInitializedThingUpdaterActor(TestProbe.apply(actorSystem), TestProbe.apply(actorSystem));
    }

    private ActorRef createInitializedThingUpdaterActor(final TestProbe thingsShardProbe,
            final TestProbe policiesShardProbe) {
        return createInitializedThingUpdaterActor(thingsShardProbe, policiesShardProbe,
                ThingUpdater.DEFAULT_THINGS_TIMEOUT);
    }

    /**
     * Creates a ThingUpdater initialized with schemaVersion = V_1 and sequenceNumber = 0L.
     */
    private ActorRef createInitializedThingUpdaterActor(final TestProbe thingsShardProbe,
            final TestProbe policiesShardProbe,
            final java.time.Duration thingsTimeout) {
        return createInitializedThingUpdaterActor(thingsShardProbe, policiesShardProbe, thingsTimeout, V_1);
    }

    /**
     * Creates a ThingUpdater initialized with schemaVersion = V_1 and sequenceNumber = 0L.
     */
    private ActorRef createInitializedThingUpdaterActor(final TestProbe thingsShardProbe,
            final TestProbe policiesShardProbe,
            final java.time.Duration thingsTimeout,
            final JsonSchemaVersion schemaVersion) {

        // prepare persistence mock for initial synchronization
        when(persistenceMock.insertOrUpdate(any(), anyLong(), anyLong())).thenReturn(Source.single(true));

        final ThingBuilder.FromScratch thingBuilder = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setRevision(0L);
        final Thing initialThing;
        if (V_1.equals(schemaVersion)) {
            initialThing = thingBuilder.setPermissions(ACL).build();
        } else {
            initialThing = thingBuilder.setPolicyId(POLICY_ID).build();
            // prepare policy sync
            when(persistenceMock.updatePolicy(any(), any())).thenReturn(Source.single(true));
        }

        final ActorRef thingUpdater = createUninitializedThingUpdaterActor(thingsShardProbe.ref(),
                policiesShardProbe.ref(), orDefaultTimeout(thingsTimeout));

        final ThingCreated thingCreated = ThingCreated.of(initialThing, 0L, DittoHeaders.empty());
        thingUpdater.tell(thingCreated, thingsShardProbe.ref());

        if (!V_1.equals(schemaVersion)) {
            final Policy policy = Policy.newBuilder(POLICY_ID).setRevision(INITIAL_POLICY_REVISION).build();
            expectRetrievePolicyAndAnswer(policy, policiesShardProbe, thingUpdater, null,
                    DittoHeaders.newBuilder().schemaVersion(schemaVersion).build());
        }

        // wait until actor becomes `awaitingSyncResult` and is ready to stash & process other messages
        waitUntil().insertOrUpdate(any(), anyLong(), anyLong());

        return thingUpdater;
    }

    /**
     * Creates an uninitialized ThingUpdater.
     */
    private ActorRef createUninitializedThingUpdaterActor(final ActorRef thingsShard, final ActorRef policiesShard,
            final java.time.Duration thingsTimeout) {

        final CircuitBreaker circuitBreaker =
                new CircuitBreaker(actorSystem.dispatcher(), actorSystem.scheduler(), 5, Duration.create(30, "s"),
                        Duration.create(1, "min"));

        final Props props = ThingUpdater.props(persistenceMock, circuitBreaker, thingsShard, policiesShard,
                java.time.Duration.ofSeconds(60), orDefaultTimeout(thingsTimeout), 100)
                .withMailbox("akka.actor.custom-updater-mailbox");

        return actorSystem.actorOf(props, THING_ID);
    }

    private ActorRef createUninitializedThingUpdaterActor(final ActorRef thingsShard, final ActorRef policiesShard) {
        return createUninitializedThingUpdaterActor(thingsShard, policiesShard, ThingUpdater.DEFAULT_THINGS_TIMEOUT);
    }

    /**
     * Wait until a call is made on the persistence mock.
     */
    private ThingsSearchUpdaterPersistence waitUntil() {
        return verify(persistenceMock, timeout(MOCKITO_TIMEOUT));
    }

    /**
     * Provides the persistence mock with fresh streams of answers for a policy update.
     */
    private void refreshPolicyUpdateAnswers(final long thingRevision, final String policyId,
            final long policyRevision) {
        when(persistenceMock.insertOrUpdate(any(), anyLong(), anyLong())).thenReturn(Source.single(true));
        when(persistenceMock.updatePolicy(any(), any())).thenReturn(Source.single(true));
        when(persistenceMock.getThingMetadata(any())).thenReturn(
                Source.single(new ThingMetadata(thingRevision, policyId, policyRevision)));
    }

    private static ThingEvent<?> createInvalidThingEvent() {
        final DittoHeaders headers = DittoHeaders.newBuilder().schemaVersion(JsonSchemaVersion.V_1).build();
        final ThingEvent<?> invalidThingEvent = mock(ThingEvent.class);
        when(invalidThingEvent.getRevision()).thenReturn(1L);
        when(invalidThingEvent.getDittoHeaders()).thenReturn(headers);
        // null type makes the event invalid!!
        when(invalidThingEvent.getType()).thenReturn(null);
        return invalidThingEvent;
    }

    private void expectRetrieveThingAndPolicy(final Thing thing, final Policy policy,
            final ActorRef thingUpdater, final TestProbe thingsActor, final TestProbe policiesActor) {
        expectRetrieveThingAndAnswer(thing, thingsActor, thingUpdater);
        expectRetrievePolicyAndAnswer(policy, policiesActor, thingUpdater, null, DittoHeaders.empty());
    }

    private void expectRetrieveThingAndAnswer(final Thing thing,
            final TestProbe thingsActor, final ActorRef thingUpdater) {
        expectShardedSudoRetrieveThing(thingsActor, thing.getId().orElseThrow(IllegalStateException::new));

        thingUpdater.tell(createSudoRetrieveThingsResponse(thing, DittoHeaders.empty()),
                ActorRef.noSender());
    }

    private static SudoRetrieveThingResponse createSudoRetrieveThingsResponse(final Thing thing,
            final DittoHeaders headers) {

        final JsonSchemaVersion schemaVersion = headers.getSchemaVersion().orElse(JsonSchemaVersion.LATEST);
        final JsonObject thingJson = thing.toJson(schemaVersion, FieldType.all());
        return SudoRetrieveThingResponse.of(thingJson, headers);
    }

    private java.time.Duration orDefaultTimeout(final java.time.Duration duration) {
        return duration != null ? duration : ThingUpdater.DEFAULT_THINGS_TIMEOUT;
    }

    private ShardedMessageEnvelope expectShardedSudoRetrieveThing(final TestProbe thingsShardProbe,
            final String thingId) {
        final ShardedMessageEnvelope envelope = thingsShardProbe.expectMsgClass(ShardedMessageEnvelope.class);
        assertThat(envelope.getType()).isEqualTo(SudoRetrieveThing.TYPE);
        assertThat(envelope.getId()).isEqualTo(thingId);
        return envelope;
    }


    private scala.concurrent.duration.FiniteDuration toScala(final java.time.Duration duration) {
        return scala.concurrent.duration.Duration.fromNanos(duration.toNanos());
    }
}
