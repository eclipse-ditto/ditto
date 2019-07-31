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
package org.eclipse.ditto.services.thingsearch.updater.actors;

import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AccessControlListModelFactory;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.Permission;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.model.things.ThingsModelFactory;
import org.eclipse.ditto.services.models.policies.PolicyReferenceTag;
import org.eclipse.ditto.services.models.policies.PolicyTag;
import org.eclipse.ditto.services.models.things.ThingTag;
import org.eclipse.ditto.services.thingsearch.persistence.write.model.Metadata;
import org.eclipse.ditto.services.utils.akka.streaming.StreamAck;
import org.eclipse.ditto.services.utils.cluster.DistPubSubAccess;
import org.eclipse.ditto.signals.commands.common.Shutdown;
import org.eclipse.ditto.signals.commands.common.ShutdownReasonFactory;
import org.eclipse.ditto.signals.events.things.ThingCreated;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.eclipse.ditto.signals.events.things.ThingModified;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Unit test for {@link ThingUpdater}.
 */
public final class ThingUpdaterTest {

    private static final String NAMESPACE = "abc";

    private static final String THING_ID = NAMESPACE + ":myId";

    private static final long REVISION = 1L;

    private static final AuthorizationSubject AUTHORIZATION_SUBJECT = AuthorizationSubject.newInstance("sid");

    private static final AclEntry ACL_ENTRY =
            AclEntry.newInstance(AUTHORIZATION_SUBJECT, AccessControlListModelFactory.allPermissions());

    private static final AccessControlList ACL = AccessControlListModelFactory.newAcl(ACL_ENTRY);

    private final Thing thing = ThingsModelFactory.newThingBuilder().setId(THING_ID).setRevision(REVISION).build();

    private ActorSystem actorSystem;
    private TestProbe pubSubTestProbe;
    private TestProbe changeQueueTestProbe;

    @Before
    public void setUpBase() {
        final Config config = ConfigFactory.load("test");
        startActorSystem(config);
    }


    @After
    public void tearDownBase() {
        shutdownActorSystem();
    }

    private void startActorSystem(final Config config) {
        shutdownActorSystem();
        actorSystem = ActorSystem.create("AkkaTestSystem", config);
        pubSubTestProbe = TestProbe.apply(actorSystem);
        changeQueueTestProbe = TestProbe.apply(actorSystem);
    }

    private void shutdownActorSystem() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
            actorSystem = null;
        }
        pubSubTestProbe = null;
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

                final ActorRef underTest = createThingUpdaterActor();

                final ThingEvent thingCreated = ThingCreated.of(thingWithAcl, 1L, dittoHeaders);
                underTest.tell(thingCreated, getRef());

                changeQueueTestProbe.expectMsg(Metadata.of(THING_ID, 1L, "", -1L));
            }
        };
    }

    @Test
    public void thingTagWithHigherSequenceNumberTriggersSync() {
        final long revision = 7L;
        final Thing currentThing = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setRevision(revision)
                .setPermissions(ACL)
                .build();
        final long thingTagRevision = revision + 9L;
        final ThingTag thingTag = ThingTag.of(THING_ID, thingTagRevision);

        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createThingUpdaterActor();

                underTest.tell(ThingModified.of(currentThing, revision, DittoHeaders.empty()), ActorRef.noSender());
                changeQueueTestProbe.expectMsg(Metadata.of(THING_ID, revision, "", -1L));

                underTest.tell(thingTag, ActorRef.noSender());
                changeQueueTestProbe.expectMsg(Metadata.of(THING_ID, thingTagRevision, "", -1L));
            }
        };
    }

    @Test
    public void thingTagWithLowerSequenceNumberDoesNotTriggerSync() {
        final long revision = 7L;
        final Thing currentThing = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setRevision(revision)
                .setPermissions(ACL)
                .build();
        final long thingTagRevision = revision - 2L;
        final ThingTag thingTag = ThingTag.of(THING_ID, thingTagRevision);

        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createThingUpdaterActor();

                underTest.tell(ThingModified.of(currentThing, revision, DittoHeaders.empty()), ActorRef.noSender());
                changeQueueTestProbe.expectMsg(Metadata.of(THING_ID, revision, "", -1L));

                underTest.tell(thingTag, ActorRef.noSender());
                changeQueueTestProbe.expectNoMessage();
            }
        };
    }

    @Test
    public void policyReferenceTagTriggersPolicyUpdate() {
        final long newPolicyRevision = REVISION + 2L;
        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createThingUpdaterActor();

                underTest.tell(PolicyReferenceTag.of(THING_ID, PolicyTag.of(THING_ID, newPolicyRevision)),
                        ActorRef.noSender());
                changeQueueTestProbe.expectMsg(Metadata.of(THING_ID, -1L, THING_ID, newPolicyRevision));

                underTest.tell(PolicyReferenceTag.of(THING_ID, PolicyTag.of(THING_ID, REVISION)),
                        ActorRef.noSender());
                changeQueueTestProbe.expectNoMessage();
            }
        };
    }

    @Test
    public void policyIdChangeTriggersSync() {
        final String policy1Id = "policy:1";
        final String policy2Id = "policy:2";

        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createThingUpdaterActor();

                // establish policy ID
                underTest.tell(PolicyReferenceTag.of(THING_ID, PolicyTag.of(policy1Id, 99L)),
                        ActorRef.noSender());
                changeQueueTestProbe.expectMsg(Metadata.of(THING_ID, -1L, policy1Id, 99L));

                underTest.tell(PolicyReferenceTag.of(THING_ID, PolicyTag.of(policy2Id, 9L)),
                        ActorRef.noSender());
                changeQueueTestProbe.expectMsg(Metadata.of(THING_ID, -1L, policy2Id, 9L));
            }
        };
    }

    @Test
    public void acknowledgesSuccessfulSync() {
        final long thingTagRevision = 7L;
        final long thingRevision = 20L;

        final Thing currentThing = ThingsModelFactory.newThingBuilder()
                .setId(THING_ID)
                .setRevision(thingRevision)
                .setPermissions(ACL)
                .build();

        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createThingUpdaterActor();

                // GIVEN: a ThingTag with nonempty sender triggers synchronization
                final ThingTag thingTag = ThingTag.of(THING_ID, thingTagRevision);
                underTest.tell(thingTag, getRef());

                // THEN: success is acknowledged
                expectMsgEquals(StreamAck.success(thingTag.asIdentifierString()));
            }
        };
    }

    @Test
    public void acknowledgesSkippedSync() {
        final long thingTagRevision = 59L;
        final long outdatedRevision = 5L;

        new TestKit(actorSystem) {
            {
                final ActorRef underTest = createThingUpdaterActor();

                // GIVEN: a ThingTag with nonempty sender triggered synchronization
                final ThingTag thingTag = ThingTag.of(THING_ID, thingTagRevision);
                underTest.tell(thingTag, getRef());
                expectMsgEquals(StreamAck.success(thingTag.asIdentifierString()));
                changeQueueTestProbe.expectMsgClass(Metadata.class);

                // WHEN: updater receives outdated ThingTag
                final ThingTag outdatedThingTag = ThingTag.of(THING_ID, outdatedRevision);
                underTest.tell(outdatedThingTag, getRef());

                // THEN: success is acknowledged
                expectMsgEquals(StreamAck.success(outdatedThingTag.asIdentifierString()));
            }
        };
    }

    @Test
    public void shutdownOnCommand() {
        new TestKit(actorSystem) {
            {
                final ActorRef underTest = watch(createThingUpdaterActor());

                final DistributedPubSubMediator.Subscribe subscribe =
                        DistPubSubAccess.subscribe(Shutdown.TYPE, underTest);
                pubSubTestProbe.expectMsg(subscribe);
                pubSubTestProbe.reply(new DistributedPubSubMediator.SubscribeAck(subscribe));

                underTest.tell(Shutdown.getInstance(ShutdownReasonFactory.getPurgeNamespaceReason(NAMESPACE),
                        DittoHeaders.empty()), pubSubTestProbe.ref());
                expectTerminated(underTest);
            }
        };

    }

    private ActorRef createThingUpdaterActor() {
        return actorSystem.actorOf(
                ThingUpdater.props(pubSubTestProbe.ref(), changeQueueTestProbe.ref()),
                THING_ID);
    }
}
