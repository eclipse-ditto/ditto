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

import static org.eclipse.ditto.json.JsonFactory.newPointer;
import static org.eclipse.ditto.json.JsonFactory.newValue;
import static org.eclipse.ditto.model.base.assertions.DittoBaseAssertions.assertThat;
import static org.eclipse.ditto.model.base.json.JsonSchemaVersion.V_1;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import org.eclipse.ditto.json.JsonObject;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.base.json.FieldType;
import org.eclipse.ditto.model.policies.Policy;
import org.eclipse.ditto.model.policiesenforcers.PolicyEnforcer;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AccessControlListModelFactory;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.Permission;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicy;
import org.eclipse.ditto.services.models.policies.commands.sudo.SudoRetrievePolicyResponse;
import org.eclipse.ditto.services.models.things.ThingTag;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThing;
import org.eclipse.ditto.services.models.things.commands.sudo.SudoRetrieveThingResponse;
import org.eclipse.ditto.services.models.thingsearch.commands.sudo.SyncThing;
import org.eclipse.ditto.services.utils.akka.JavaTestProbe;
import org.eclipse.ditto.services.utils.cluster.ShardedMessageEnvelope;
import org.eclipse.ditto.services.utils.distributedcache.actors.CacheFacadeActor;
import org.eclipse.ditto.services.utils.distributedcache.actors.CacheRole;
import org.eclipse.ditto.signals.events.policies.PolicyModified;
import org.eclipse.ditto.signals.events.things.AttributeCreated;
import org.eclipse.ditto.signals.events.things.AttributeModified;
import org.eclipse.ditto.signals.events.things.ThingCreated;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.eclipse.ditto.signals.events.things.ThingModified;
import org.eclipse.ditto.services.thingsearch.persistence.write.ThingMetadata;
import org.eclipse.ditto.services.thingsearch.persistence.write.ThingsSearchUpdaterPersistence;
import org.eclipse.ditto.services.thingsearch.persistence.write.impl.CombinedThingWrites;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.pattern.CircuitBreaker;
import akka.stream.javadsl.Source;
import akka.testkit.JavaTestKit;
import akka.testkit.TestProbe;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

/**
 * Unit test for {@link ThingUpdater}.
 */
@RunWith(MockitoJUnitRunner.class)
public final class ThingUpdaterTest {

    private static final String POLICY_ID = "abc:policy";
    private static final String THING_ID = "abc:myId";

    private static final long REVISION = 1L;

    static final AuthorizationSubject AUTHORIZATION_SUBJECT = AuthorizationSubject.newInstance("sid");

    static final AclEntry ACL_ENTRY =
            AclEntry.newInstance(AUTHORIZATION_SUBJECT, AccessControlListModelFactory.allPermissions());

    static final AccessControlList ACL = AccessControlListModelFactory.newAcl(ACL_ENTRY);

    private final Thing thing = ThingsModelFactory.newThingBuilder().setId(THING_ID).setRevision(REVISION).build();

    // how many ms to wait for a mockito call
    private static final int MOCKITO_TIMEOUT = 1000;

    private ActorSystem actorSystem;

    @Mock
    private ThingsSearchUpdaterPersistence persistenceMock;

    @Mock
    private PolicyEnforcer policyEnforcerMock;


    private Source<Boolean, NotUsed> successWithDelay() {
        final int sourceDelayMillis = 500;
        return Source.single(Boolean.TRUE).initialDelay(Duration.create(sourceDelayMillis, TimeUnit.MILLISECONDS));
    }

    /** */
    @Before
    public void setUpBase() {
        final Config config = ConfigFactory.load("test");
        actorSystem = ActorSystem.create("AkkaTestSystem", config);
        Mockito.reset(persistenceMock, policyEnforcerMock);
        when(persistenceMock.getThingMetadata(any())).thenReturn(Source.single(new ThingMetadata(-1L, null, -1L)));
    }

    /** */
    @After
    public void tearDownBase() {
        if (actorSystem != null) {
            JavaTestKit.shutdownActorSystem(actorSystem);
            actorSystem = null;
        }
    }

    @Test
    public void createThing() throws InterruptedException {
        // disable policy load
        final DittoHeaders dittoHeaders = DittoHeaders.empty();
        new JavaTestKit(actorSystem) {
            {
                final Thing thingWithAcl =
                        thing.setAclEntry(AclEntry.newInstance(AuthorizationSubject.newInstance("user1"),
                                Permission.READ)).toBuilder().setRevision(1L).build();

                final ActorRef underTest = createThingUpdaterActor();

                final ThingEvent thingCreated = ThingCreated.of(thingWithAcl, 1L, dittoHeaders);
                underTest.tell(thingCreated, getRef());

                waitUntil().insertOrUpdate(eq(thingWithAcl), eq(1L), anyLong());
            }
        };
    }

    @Test
    @Ignore("currently does not run every time on Travis - ignoring for now")
    public void concurrentUpdates() throws InterruptedException {
        final Thing thingWithAcl = thing.setAclEntry(AclEntry.newInstance(AuthorizationSubject.newInstance("user1"),
                Permission.READ));
        assertEquals(V_1, thingWithAcl.getImplementedSchemaVersion());
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder().schemaVersion(V_1).build();

        final CombinedThingWrites expectedWrites1 = CombinedThingWrites.newBuilder(1L, policyEnforcerMock)
                .addEvent(createAttributeModified("p0", newValue(true), 2L), V_1)
                .build();
        final CombinedThingWrites expectedWrites2 = CombinedThingWrites.newBuilder(2L, policyEnforcerMock)
                .addEvent(createAttributeModified("p1", newValue(true), 3L), V_1)
                .addEvent(createAttributeModified("p2", newValue(true), 4L), V_1)
                .addEvent(createAttributeModified("p3", newValue(true), 5L), V_1)
                .build();
        when(persistenceMock.executeCombinedWrites(eq(THING_ID), eq(expectedWrites1))).thenReturn(successWithDelay());

        new JavaTestKit(actorSystem) {
            {
                final ActorRef underTest = createThingUpdaterActor();

                final ThingEvent thingCreated = ThingCreated.of(thingWithAcl, 1L, dittoHeaders);

                //This processing will cause that the mocked mongoDB operation to last for 500 ms
                underTest.tell(thingCreated, getRef());
                waitUntil().insertOrUpdate(eq(thingWithAcl), eq(1L), eq(-1L));

                final ThingEvent attributeCreated0 =
                        AttributeCreated.of(THING_ID, newPointer("p0"), newValue(true), 2L, dittoHeaders);

                final ThingEvent attributeCreated1 =
                        AttributeCreated.of(THING_ID, newPointer("p1"), newValue(true), 3L, dittoHeaders);

                final ThingEvent attributeCreated2 =
                        AttributeCreated.of(THING_ID, newPointer("p2"), newValue(true), 4L, dittoHeaders);

                final ThingEvent attributeCreated3 =
                        AttributeCreated.of(THING_ID, newPointer("p3"), newValue(true), 5L, dittoHeaders);

                underTest.tell(attributeCreated0, getRef());

                // the following events will be executed all together as they are processed while another operation is
                // in progress
                underTest.tell(attributeCreated1, getRef());
                underTest.tell(attributeCreated2, getRef());
                underTest.tell(attributeCreated3, getRef());

                waitUntil().executeCombinedWrites(eq(THING_ID), eq(expectedWrites2));
            }
        };
    }

    private static AttributeModified createAttributeModified(final CharSequence attributePointer,
            final JsonValue attributeValue, final long revision) {
        return AttributeModified.of(THING_ID, newPointer(attributePointer), attributeValue, revision,
                DittoHeaders.empty());
    }

    @Test
    public void unexpectedHighSequenceNumberTriggersSync() throws InterruptedException {
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
                final ActorRef underTest = createThingUpdaterActor(thingsShardProbe, policiesShardProbe);
                // will cause that a sync is triggered
                underTest.tell(attributeCreated, ref());

                // wait until SudoRetrieveThing (inside a sharding envelope) is sent
                expectShardedSudoRetrieveThing(thingsShardProbe, THING_ID);

                underTest.tell(SudoRetrieveThingResponse.of(currentThing, f -> true, dittoHeaders), ref());
                waitUntil().insertOrUpdate(eq(currentThing), eq(thingRevision), eq(-1L));
            }
        };
    }

    @Test
    @Ignore("currently does not run every time on Travis - ignoring for now")
    public void unsuccessfulUpdateTriggersSync() throws InterruptedException {
        final long revision = 4L;
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
        final CombinedThingWrites expectedWrite = CombinedThingWrites.newBuilder(0L, policyEnforcerMock)
                .addEvent(createAttributeModified("p1", newValue(true), 1L), V_1)
                .build();
        final Source<Boolean, NotUsed> sourceUnsuccess = Source.single(Boolean.FALSE);

        when(persistenceMock.executeCombinedWrites(eq(THING_ID), eq(expectedWrite))).thenReturn(sourceUnsuccess);
        when(persistenceMock.getThingMetadata(any())).thenReturn(Source.single(new ThingMetadata(revision, null, -1L)));

        new JavaTestKit(actorSystem) {
            {
                final ActorRef underTest = createThingUpdaterActor();
                // will cause that a sync is triggered
                underTest.tell(attributeCreated, getRef());
                waitUntil().executeCombinedWrites(eq(THING_ID), any());
                underTest.tell(SudoRetrieveThingResponse.of(currentThing, f -> true, dittoHeaders), getRef());
                waitUntil().insertOrUpdate(eq(currentThing), eq(revision), eq(-1L));
            }
        };
    }

    @Test
    public void databaseExceptionTriggersSync() throws InterruptedException {
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .schemaVersion(V_1)
                .build();
        final ThingEvent attributeCreated =
                AttributeCreated.of(THING_ID, newPointer("p1"), newValue(true), 1L, dittoHeaders);

        when(persistenceMock.executeCombinedWrites(eq(THING_ID), any(CombinedThingWrites.class))).thenThrow(
                new RuntimeException("db operation failed."));

        new JavaTestProbe(actorSystem) {
            {
                final TestProbe thingsShardProbe = TestProbe.apply(actorSystem);
                final TestProbe policiesShardProbe = TestProbe.apply(actorSystem);
                final ActorRef underTest = createThingUpdaterActor(thingsShardProbe, policiesShardProbe);
                // will cause that a sync is triggered
                underTest.tell(attributeCreated, ref());

                waitUntil().executeCombinedWrites(eq(THING_ID), any());

                // resync should be triggered
                expectShardedSudoRetrieveThing(thingsShardProbe, THING_ID);
            }
        };
    }

    @Test
    public void thingTagWithHigherSequenceNumberTriggersSync() throws InterruptedException {
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

        new JavaTestKit(actorSystem) {
            {
                final TestProbe thingsShardProbe = TestProbe.apply(actorSystem);
                final TestProbe policiesShardProbe = TestProbe.apply(actorSystem);

                final ActorRef underTest = createThingUpdaterActor(thingsShardProbe, policiesShardProbe);
                // will cause that a sync is triggered
                underTest.tell(thingTag, getRef());
                final ShardedMessageEnvelope shardedMessageEnvelope =
                        thingsShardProbe.expectMsgClass(FiniteDuration.apply(5, TimeUnit.SECONDS),
                                ShardedMessageEnvelope.class);
                assertEquals(expectedSudoRetrieveThing, shardedMessageEnvelope.getMessage());
                underTest.tell(SudoRetrieveThingResponse.of(currentThing, f -> true, dittoHeadersV1),
                        getRef());
                waitUntil().insertOrUpdate(eq(currentThing), eq(revision), eq(-1L));
            }
        };
    }

    @Test
    public void thingTagWithLowerSequenceNumberDoesNotTriggerSync() throws InterruptedException {
        new JavaTestKit(actorSystem) {
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

                final ActorRef underTest = createThingUpdaterActor(thingsShardProbe, policiesShardProbe);
                final ThingEvent thingCreated = ThingCreated.of(thingWithRevision, revision, dittoHeaders);

                underTest.tell(thingCreated, getRef());
                waitUntil().insertOrUpdate(eq(thingWithRevision), eq(revision), eq(-1L));

                // should trigger no sync
                underTest.tell(thingTag, getRef());
                thingsShardProbe.expectNoMsg();
            }
        };
    }

    @Test
    public void thingEventWithEqualSequenceNumberDoesNotTriggerSync() throws InterruptedException {
        new JavaTestKit(actorSystem) {
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

                final ActorRef underTest = createThingUpdaterActor(thingsShardProbe, policiesShardProbe);
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
    public void persistenceErrorForCombinedWritesTriggersSync() throws InterruptedException {
        new JavaTestKit(actorSystem) {
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

                final ActorRef underTest = createThingUpdaterActor(thingsShardProbe, policiesShardProbe);
                final ThingEvent thingCreated =
                        ThingCreated.of(thingWithRevision, revision, dittoHeaders);

                underTest.tell(thingCreated, getRef());
                waitUntil().insertOrUpdate(eq(thingWithRevision), eq(revision), eq(-1L));

                when(persistenceMock.executeCombinedWrites(any(), any()))
                        .thenThrow(new IllegalStateException("justForTest"));

                final ThingEvent changeEvent =
                        AttributeCreated.of(THING_ID, JsonPointer.of("/foo"), JsonValue.of("bar"),
                                revision + 1, dittoHeaders);
                underTest.tell(changeEvent, getRef());

                // should trigger sync
                expectShardedSudoRetrieveThing(thingsShardProbe, THING_ID);
            }
        };
    }

    @Test
    public void invalidThingEventTriggersSync() throws InterruptedException {
        new JavaTestKit(actorSystem) {
            {
                final ThingEvent<?> invalidThingEvent = createInvalidThingEvent();

                final TestProbe thingsShardProbe = TestProbe.apply(actorSystem);
                final TestProbe policiesShardProbe = TestProbe.apply(actorSystem);

                final ActorRef underTest = createThingUpdaterActor(thingsShardProbe, policiesShardProbe);
                underTest.tell(invalidThingEvent, getRef());

                // should trigger sync
                expectShardedSudoRetrieveThing(thingsShardProbe, THING_ID);
            }
        };
    }

    @Test
    public void syncTimeoutRetriggersSync() throws InterruptedException {
        // Command headers to make SudoRetrieveThingResponse return the access control list.
        final DittoHeaders dittoHeaders = DittoHeaders.newBuilder()
                .schemaVersion(V_1)
                .build();
        final SudoRetrieveThing retrieveThing = SudoRetrieveThing.withOriginalSchemaVersion(THING_ID, dittoHeaders);
        final ShardedMessageEnvelope expectedShardMessage = ShardedMessageEnvelope.of(THING_ID, SudoRetrieveThing.TYPE,
                retrieveThing.toJson(), DittoHeaders.empty());

        new JavaTestKit(actorSystem) {
            {
                final TestProbe thingsShardProbe = TestProbe.apply(actorSystem);
                final TestProbe policiesShardProbe = TestProbe.apply(actorSystem);

                // Timeout should be enough for initial synchronization to succeed, but not so long that 3 timeout
                // cause an order-of-magnitude increase in test execution time.
                final java.time.Duration underTestTimeout = java.time.Duration.of(1, ChronoUnit.NANOS);

                final ActorRef underTest =
                        createThingUpdaterActor(thingsShardProbe, policiesShardProbe, underTestTimeout);

                final Thing thingWithAcl =
                        thing.setAclEntry(AclEntry.newInstance(AuthorizationSubject.newInstance("user1"),
                                Permission.READ));

                final ThingEvent thingCreated = ThingCreated.of(thingWithAcl, 1L, dittoHeaders);

                underTest.tell(thingCreated, getRef());
                waitUntil().insertOrUpdate(eq(thingWithAcl), eq(1L), eq(-1L));

                underTest.tell(SyncThing.of(THING_ID, DittoHeaders.empty()), getRef());

                // at the moment we have 3 retries
                ShardedMessageEnvelope envelope = thingsShardProbe.expectMsgClass(ShardedMessageEnvelope.class);
                assertEquals(retrieveThing.toJson(), envelope.getMessage());
                envelope = thingsShardProbe.expectMsgClass(ShardedMessageEnvelope.class);
                assertEquals(retrieveThing.toJson(), envelope.getMessage());
                envelope = thingsShardProbe.expectMsgClass(ShardedMessageEnvelope.class);
                assertEquals(retrieveThing.toJson(), envelope.getMessage());
                watch(underTest);
                expectTerminated(underTest);
            }
        };
    }

    @Test
    public void tooManyStashedMessagesDiscardOldMessages() throws InterruptedException {
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

        when(persistenceMock.getThingMetadata(any())).thenReturn(Source.single(new ThingMetadata(1L, null, -1L)));

        new JavaTestKit(actorSystem) {
            {
                final TestProbe thingsShardProbe = TestProbe.apply(actorSystem);
                final TestProbe policiesShardProbe = TestProbe.apply(actorSystem);
                final ActorRef underTest = createThingUpdaterActor(thingsShardProbe, policiesShardProbe);

                // send a ThingCreated event and expect it to get persisted
                underTest.tell(ThingCreated.of(thingWithAcl, 1L, dittoHeaders), getRef());

                waitUntil().insertOrUpdate(eq(thingWithAcl), eq(1L), eq(-1L));

                // send a ThingTag with sequence number = 3, which is unexpected and the updater should trigger a sync
                underTest.tell(ThingTag.of(THING_ID, 3L), getRef());
                ShardedMessageEnvelope shardedMessageEnvelope =
                        thingsShardProbe.expectMsgClass(FiniteDuration.apply(15, TimeUnit.SECONDS),
                                ShardedMessageEnvelope.class);
                assertEquals(SudoRetrieveThing.TYPE, shardedMessageEnvelope.getType());
                assertEquals(THING_ID, shardedMessageEnvelope.getId());
                assertEquals(retrieveThing.toJson(), shardedMessageEnvelope.getMessage());

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
                underTest.tell(SudoRetrieveThingResponse.of(currentThing, f -> true, DittoHeaders.newBuilder()
                                .schemaVersion(V_1)
                                .build()),
                        getRef());

                // the updater persists the thing with sequence number 3 and goes back to thing event processing
                waitUntil().insertOrUpdate(eq(currentThing), eq(3L), eq(-1L));

                // because we send 4 AttributeCreated events, but the stash capacity is only 3, the event with the expected
                // sequence number 4 was dropped and we should receive the AttributeCreated event with sequence number 5
                // instead. this triggers another sync as expected
                shardedMessageEnvelope =
                        thingsShardProbe.expectMsgClass(FiniteDuration.apply(15, TimeUnit.SECONDS),
                                ShardedMessageEnvelope.class);
                assertEquals(SudoRetrieveThing.TYPE, shardedMessageEnvelope.getType());
                assertEquals(THING_ID, shardedMessageEnvelope.getId());
                assertEquals(retrieveThing.toJson(), shardedMessageEnvelope.getMessage());
            }
        };
    }

    /** */
    @Test
    public void policyEventTriggersPolicyUpdate() throws InterruptedException {
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
                SudoRetrieveThingResponse.of(thingWithPolicyId, FieldType.regularOrSpecial(), emptyDittoHeaders);

        new JavaTestKit(actorSystem) {
            {
                final TestProbe thingsShardProbe = TestProbe.apply(actorSystem);
                final TestProbe policiesShardProbe = TestProbe.apply(actorSystem);
                final ActorRef underTest = createThingUpdaterActor(thingsShardProbe, policiesShardProbe);

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

                waitUntil().updatePolicy(eq(thingWithPolicyId), any(PolicyEnforcer.class));
            }
        };
    }

    @Test
    public void policyIdChangeTriggersSync() throws InterruptedException {
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

        new JavaTestKit(actorSystem) {
            {
                final TestProbe thingsShardProbe = TestProbe.apply(actorSystem);
                final TestProbe policiesShardProbe = TestProbe.apply(actorSystem);
                final ActorRef underTest = createThingUpdaterActor(thingsShardProbe, policiesShardProbe);

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

                waitUntil().updatePolicy(eq(thingWithPolicy2), any(PolicyEnforcer.class));
            }
        };
    }

    @Test
    public void thingV1BecomesThingV2() throws InterruptedException {
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
        final SudoRetrieveThingResponse thingResponse1 =
                SudoRetrieveThingResponse.of(thingWithoutPolicy, FieldType.regularOrSpecial(),
                        DittoHeaders.empty());
        final SudoRetrieveThingResponse thingResponse2 =
                SudoRetrieveThingResponse.of(thingWithPolicy, FieldType.regularOrSpecial(),
                        DittoHeaders.empty());
        final ThingCreated thingCreated = ThingCreated.of(thingWithoutPolicy, 1L, DittoHeaders.empty());
        final ThingModified thingModified = ThingModified.of(thingWithPolicy, 2L, DittoHeaders.empty());

        new JavaTestKit(actorSystem) {
            {
                final TestProbe thingsShardProbe = TestProbe.apply(actorSystem);
                final TestProbe policiesShardProbe = TestProbe.apply(actorSystem);
                final ActorRef underTest = createThingUpdaterActor(thingsShardProbe, policiesShardProbe);

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

                waitUntil().updatePolicy(eq(thingWithPolicy), any(PolicyEnforcer.class));
            }
        };
    }

    private ActorRef createThingUpdaterActor() {
        return createThingUpdaterActor(TestProbe.apply(actorSystem), TestProbe.apply(actorSystem));
    }

    private ActorRef createThingUpdaterActor(final TestProbe thingsShardProbe, final TestProbe policiesShardProbe) {
        return createThingUpdaterActor(thingsShardProbe, policiesShardProbe, ThingUpdater.DEFAULT_THINGS_TIMEOUT);
    }

    /**
     * Creates a ThingUpdater initialized with schemaVersion = V_1 and sequenceNumber = 0L.
     */
    private ActorRef createThingUpdaterActor(final TestProbe thingsShardProbe, final TestProbe policiesShardProbe,
            final java.time.Duration thingsTimeout) {

        // prepare persistence mock for initial synchronization
        when(persistenceMock.insertOrUpdate(any(), anyLong(), anyLong())).thenReturn(Source.single(true));

        final Thing initialThing = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setRevision(0L)
                .setPermissions(ACL)
                .build();

        final CircuitBreaker circuitBreaker =
                new CircuitBreaker(actorSystem.dispatcher(), actorSystem.scheduler(), 5, Duration.create(30, "s"),
                        Duration.create(1, "min"));

        final ActorRef thingCacheFacade = actorSystem.actorOf(CacheFacadeActor.props(CacheRole.THING,
                actorSystem.settings().config()), CacheFacadeActor.actorNameFor(CacheRole.THING));

        final ActorRef policyCacheFacade = actorSystem.actorOf(CacheFacadeActor.props(CacheRole.POLICY,
                actorSystem.settings().config()), CacheFacadeActor.actorNameFor(CacheRole.POLICY));

        final Props props =
                ThingUpdater.props(persistenceMock, circuitBreaker, thingsShardProbe.ref(),
                        policiesShardProbe.ref(), java.time.Duration.ofSeconds(60), thingsTimeout, thingCacheFacade,
                        policyCacheFacade)
                        .withMailbox("akka.actor.custom-updater-mailbox");

        final ActorRef thingUpdater = actorSystem.actorOf(props, THING_ID);

        expectShardedSudoRetrieveThing(thingsShardProbe, THING_ID);
        thingUpdater.tell(SudoRetrieveThingResponse.of(initialThing, FieldType.regularOrSpecial(),
                DittoHeaders.empty()), thingsShardProbe.ref());

        // wait until actor becomes `awaitingSyncResult` and is ready to stash & process other messages
        waitUntil().insertOrUpdate(any(), anyLong(), anyLong());

        return thingUpdater;
    }

    /**
     * Wait until a call is made on the persistence mock.
     */
    private ThingsSearchUpdaterPersistence waitUntil() {
        return verify(persistenceMock, timeout(MOCKITO_TIMEOUT));
    }

    private static ThingEvent<?> createInvalidThingEvent() {
        final ThingEvent<?> invalidThingEvent = mock(ThingEvent.class);
        when(invalidThingEvent.getRevision()).thenReturn(1L);
        when(invalidThingEvent.getDittoHeaders()).thenReturn(DittoHeaders.empty());
        // null type makes the event invalid!!
        when(invalidThingEvent.getType()).thenReturn(null);
        return invalidThingEvent;
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

    private void expectShardedSudoRetrieveThing(final TestProbe thingsShardProbe, final String thingId) {
        final ShardedMessageEnvelope envelope = thingsShardProbe.expectMsgClass(ShardedMessageEnvelope.class);
        assertThat(envelope.getType()).isEqualTo(SudoRetrieveThing.TYPE);
        assertThat(envelope.getId()).isEqualTo(thingId);
    }
}
