/*
 * Copyright (c) 2021 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.concierge.service.starter.actors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.cache.Cache;
import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.internal.utils.cacheloaders.EnforcementCacheKey;
import org.eclipse.ditto.internal.utils.cacheloaders.PolicyEnforcer;
import org.eclipse.ditto.json.JsonPointer;
import org.eclipse.ditto.json.JsonValue;
import org.eclipse.ditto.policies.model.Policy;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.signals.events.PolicyEvent;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.events.AttributeModified;
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
 * Tests {@link org.eclipse.ditto.concierge.service.starter.actors.PolicyCacheUpdateActor}.
 */
public final class PolicyCacheUpdateActorTest {

    private static final PolicyId POLICY_ID = PolicyId.of("my.namespace:policy_id");
    private static final EnforcementCacheKey ENTITY_ID = EnforcementCacheKey.of(POLICY_ID);
    private static final DittoHeaders DITTO_HEADERS = DittoHeaders.empty();
    private static final int REVISION = 1;

    private static ActorSystem system;

    private Cache<EnforcementCacheKey, Entry<Policy>> mockPolicyCache;
    private Cache<EnforcementCacheKey, Entry<PolicyEnforcer>> mockEnforcerCache;

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
        mockPolicyCache = mock(Cache.class);
        mockEnforcerCache = mock(Cache.class);

        pubSubMediatorProbe = new TestProbe(system, "mockPubSubMediator");

        final Props props = PolicyCacheUpdateActor.props(mockPolicyCache, mockEnforcerCache, pubSubMediatorProbe.ref());
        updateActor = system.actorOf(props);

        testKit = new TestKit(system);
    }

    @Test
    public void actorSubscribesViaPubSub() {
        final DistributedPubSubMediator.Subscribe subscribe =
                pubSubMediatorProbe.expectMsgClass(DistributedPubSubMediator.Subscribe.class);
        assertThat(subscribe.topic()).isEqualTo(PolicyEvent.TYPE_PREFIX);
        assertThat(subscribe.ref()).isEqualTo(updateActor);
    }

    @Test
    public void arbitraryPolicyEventTriggersInvalidation() {
        final PolicyEvent<?> arbitraryPolicyEvent = mock(PolicyEvent.class);
        when(arbitraryPolicyEvent.getEntityId()).thenReturn(POLICY_ID);

        sendEvent(arbitraryPolicyEvent);

        assertInvalidation(true);
    }

    @Test
    public void irrelevantEventDoesNotTriggerAnyInvalidation() {
        final AttributeModified irrelevantEvent =
                AttributeModified.of(ThingId.of("my.namespace:thing_id"), JsonPointer.of("foo"), JsonValue.of("bar"),
                        REVISION, null, DITTO_HEADERS, null);
        sendEvent(irrelevantEvent);

        assertInvalidation(false);
    }

    private void sendEvent(final Object message) {
        updateActor.tell(message, testKit.getRef());
    }

    private void assertInvalidation(final boolean invalidate) {
        awaitAssert(() -> assertInvalidationWithoutWait(invalidate));
    }

    private void awaitAssert(final Runnable r) {
        testKit.awaitAssert(() -> {
            r.run();
            return null;
        });
    }

    private void assertInvalidationWithoutWait(final boolean invalidate) {
        if (invalidate) {
            verify(mockEnforcerCache).invalidate(ENTITY_ID);
        } else {
            Mockito.verifyNoMoreInteractions(mockEnforcerCache);
        }
    }

}
