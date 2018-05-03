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
package org.eclipse.ditto.services.concierge.cache.update;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Collections;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.auth.AuthorizationSubject;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.model.things.AccessControlList;
import org.eclipse.ditto.model.things.AclEntry;
import org.eclipse.ditto.model.things.Thing;
import org.eclipse.ditto.services.models.concierge.EntityId;
import org.eclipse.ditto.services.models.concierge.cache.Entry;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.signals.commands.things.ThingCommand;
import org.eclipse.ditto.signals.events.things.AclEntryCreated;
import org.eclipse.ditto.signals.events.things.AclEntryDeleted;
import org.eclipse.ditto.signals.events.things.AclEntryModified;
import org.eclipse.ditto.signals.events.things.AclModified;
import org.eclipse.ditto.signals.events.things.AttributeModified;
import org.eclipse.ditto.signals.events.things.PolicyIdModified;
import org.eclipse.ditto.signals.events.things.ThingCreated;
import org.eclipse.ditto.signals.events.things.ThingDeleted;
import org.eclipse.ditto.signals.events.things.ThingEvent;
import org.eclipse.ditto.signals.events.things.ThingModified;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

/**
 * Tests {@link ThingCacheUpdateActor}.
 */
public final class ThingCacheUpdateActorTest {

    private static final int INSTANCE_INDEX = 0;
    private static final String ID = "my.namespace:id";
    private static final String POLICY_ID = "my.namespace:policy_id";
    private static final EntityId ENTITY_ID = EntityId.of(ThingCommand.RESOURCE_TYPE, ID);
    private static final AccessControlList ACL = AccessControlList.newBuilder().build();
    private static final AuthorizationSubject AUTH_SUBJECT = AuthorizationSubject.newInstance("testSubject");
    private static final AclEntry ACL_ENTRY =
            AclEntry.newInstance(AUTH_SUBJECT, Collections.emptySet());
    private static final Thing THING = Thing.newBuilder().setId(ID).build();
    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.empty();
    private static final int REVISION = 1;

    private static ActorSystem system;

    private Cache<EntityId, Entry<Enforcer>> mockAclEnforcerCache;
    private Cache<EntityId, Entry<EntityId>> mockIdCache;

    private ActorRef updateActor;
    private TestKit testKit;
    private TestProbe pubSubMediatorProbe;

    @BeforeClass
    public static void beforeClass() {
        system = ActorSystem.create();
    }

    @AfterClass
    public static void shutdown() {
        if (system != null) {
            TestKit.shutdownActorSystem(system);
        }
    }

    @Before
    @SuppressWarnings("unchecked")
    public void init() {
        mockAclEnforcerCache = mock(Cache.class);
        mockIdCache = mock(Cache.class);

        pubSubMediatorProbe = new TestProbe(system, "mockPubSubMediator");

        final Props props = ThingCacheUpdateActor.props(mockAclEnforcerCache, mockIdCache,
                pubSubMediatorProbe.ref(), INSTANCE_INDEX);
        updateActor = system.actorOf(props);

        testKit = new TestKit(system);
    }

    @Test
    public void actorSubscribesViaPubSub() {
        final DistributedPubSubMediator.Subscribe subscribe =
                pubSubMediatorProbe.expectMsgClass(DistributedPubSubMediator.Subscribe.class);
        Assertions.assertThat(subscribe.topic()).isEqualTo(ThingEvent.TYPE_PREFIX);
        Assertions.assertThat(subscribe.ref()).isEqualTo(updateActor);
    }

    @Test
    public void aclModifiedTriggersEnforcerInvalidation() {
        sendEvent(AclModified.of(ID, ACL, REVISION, DITTO_HEADERS));

        assertInvalidation(false,true);
    }

    @Test
    public void aclEntryCreatedTriggersEnforcerInvalidation() {
        sendEvent(AclEntryCreated.of(ID, ACL_ENTRY, REVISION, DITTO_HEADERS));

        assertInvalidation(false, true);
    }

    @Test
    public void aclEntryModifiedTriggersEnforcerInvalidation() {
        sendEvent(AclEntryModified.of(ID, ACL_ENTRY, REVISION, DITTO_HEADERS));

        assertInvalidation(false, true);
    }

    @Test
    public void aclEntryDeletedTriggersEnforcerInvalidation() {
        sendEvent(AclEntryDeleted.of(ID, AUTH_SUBJECT, REVISION, DITTO_HEADERS));

        assertInvalidation(false, true);
    }

    @Test
    public void policyModifiedTriggersIdAndEnforcerInvalidation() {
        sendEvent(PolicyIdModified.of(ID, POLICY_ID, REVISION, DITTO_HEADERS));

        assertInvalidation(true, true);
    }

    @Test
    public void thingCreatedTriggersIdAndEnforcerInvalidation() {
        sendEvent(ThingCreated.of(THING, REVISION, DITTO_HEADERS));

        assertInvalidation(true, true);
    }

    @Test
    public void thingModifiedTriggersIdAndEnforcerInvalidation() {
        sendEvent(ThingModified.of(THING, REVISION, DITTO_HEADERS));

        assertInvalidation(true, true);
    }

    @Test
    public void thingDeletedTriggersIdAndEnforcerInvalidation() {
        sendEvent(ThingDeleted.of(ID, REVISION, DITTO_HEADERS));

        assertInvalidation(true, true);
    }

    @Test
    public void irrelevantEventDoesNotTriggerAnyInvalidation() {
        final AttributeModified irrelevantEvent =
                AttributeModified.of(ID, JsonPointer.of("foo"), JsonValue.of("bar"),
                        REVISION, DITTO_HEADERS);
        sendEvent(irrelevantEvent);

        assertInvalidation(false, false);
    }

    private void sendEvent(final Object message) {
        updateActor.tell(message, testKit.getRef());
    }

    private void assertInvalidation(final boolean idCache, final boolean enforcerCache) {
        awaitAssert(() ->  assertInvalidationWithoutWait(idCache,  enforcerCache));
    }

    private void awaitAssert(final Runnable r) {
        testKit.awaitAssert(() -> {r.run(); return null; });
    }

    private void assertInvalidationWithoutWait(final boolean idCache, final boolean enforcerCache) {
        if (idCache) {
            verify(mockIdCache).invalidate(ENTITY_ID);
        } else {
            Mockito.verifyZeroInteractions(mockIdCache);
        }

        if (enforcerCache) {
            verify(mockAclEnforcerCache).invalidate(ENTITY_ID);
        } else {
            Mockito.verifyZeroInteractions(mockAclEnforcerCache);
        }
    }

}
