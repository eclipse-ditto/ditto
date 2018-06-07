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
import static org.mockito.Mockito.when;

import org.assertj.core.api.Assertions;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.model.base.headers.DittoHeaders;
import org.eclipse.ditto.model.enforcers.Enforcer;
import org.eclipse.ditto.services.models.concierge.EntityId;
import org.eclipse.ditto.services.models.concierge.cache.Entry;
import org.eclipse.ditto.services.utils.cache.Cache;
import org.eclipse.ditto.signals.commands.policies.PolicyCommand;
import org.eclipse.ditto.signals.events.policies.PolicyEvent;
import org.eclipse.ditto.signals.events.things.AttributeModified;
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
 * Tests {@link PolicyCacheUpdateActor}.
 */
public final class PolicyCacheUpdateActorTest {

    private static final int INSTANCE_INDEX = 0;
    private static final String POLICY_ID = "my.namespace:policy_id";
    private static final EntityId ENTITY_ID = EntityId.of(PolicyCommand.RESOURCE_TYPE, POLICY_ID);
    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.empty();
    private static final int REVISION = 1;

    private static ActorSystem system;

    private Cache<EntityId, Entry<Enforcer>> mockEnforcerCache;

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
        mockEnforcerCache = mock(Cache.class);

        pubSubMediatorProbe = new TestProbe(system, "mockPubSubMediator");

        final Props props = PolicyCacheUpdateActor.props(mockEnforcerCache, pubSubMediatorProbe.ref(), INSTANCE_INDEX);
        updateActor = system.actorOf(props);

        testKit = new TestKit(system);
    }

    @Test
    public void actorSubscribesViaPubSub() {
        final DistributedPubSubMediator.Subscribe subscribe =
                pubSubMediatorProbe.expectMsgClass(DistributedPubSubMediator.Subscribe.class);
        Assertions.assertThat(subscribe.topic()).isEqualTo(PolicyEvent.TYPE_PREFIX);
        Assertions.assertThat(subscribe.ref()).isEqualTo(updateActor);
    }

    @Test
    public void arbitraryPolicyEventTriggersInvalidation() {
        final PolicyEvent arbitraryPolicyEvent = mock(PolicyEvent.class);
        when(arbitraryPolicyEvent.getId()).thenReturn(POLICY_ID);
        when(arbitraryPolicyEvent.getPolicyId()).thenReturn(POLICY_ID);

        sendEvent(arbitraryPolicyEvent);

        assertInvalidation(true);
    }

    @Test
    public void irrelevantEventDoesNotTriggerAnyInvalidation() {
        final AttributeModified irrelevantEvent =
                AttributeModified.of("my.namespace:thing_id", JsonPointer.of("foo"), JsonValue.of("bar"),
                        REVISION, DITTO_HEADERS);
        sendEvent(irrelevantEvent);

        assertInvalidation(false);
    }

    private void sendEvent(final Object message) {
        updateActor.tell(message, testKit.getRef());
    }

    private void assertInvalidation(final boolean invalidate) {
        awaitAssert(() ->  assertInvalidationWithoutWait(invalidate));
    }

    private void awaitAssert(final Runnable r) {
        testKit.awaitAssert(() -> {r.run(); return null; });
    }

    private void assertInvalidationWithoutWait(final boolean invalidate) {
        if (invalidate) {
            verify(mockEnforcerCache).invalidate(ENTITY_ID);
        } else {
            Mockito.verifyZeroInteractions(mockEnforcerCache);
        }
    }

}
