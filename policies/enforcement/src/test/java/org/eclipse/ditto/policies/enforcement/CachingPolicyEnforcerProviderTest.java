/*
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.policies.enforcement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.ditto.internal.utils.cache.entry.Entry;
import org.eclipse.ditto.internal.utils.namespaces.BlockedNamespaces;
import org.eclipse.ditto.policies.api.PolicyTag;
import org.eclipse.ditto.policies.model.PolicyId;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.cluster.ddata.ORSet;
import akka.cluster.ddata.Replicator;
import akka.cluster.ddata.SelfUniqueAddress;
import akka.cluster.pubsub.DistributedPubSubMediator;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

@RunWith(MockitoJUnitRunner.class)
public final class CachingPolicyEnforcerProviderTest {

    private ActorSystem actorSystem;

    private TestProbe cachingActorTestProbe;
    private TestProbe pubSubMediatorProbe;

    @Mock
    public PolicyEnforcerCache cache;

    @Mock
    public BlockedNamespaces blockedNamespaces;


    @Before
    public void setup() {
        actorSystem = ActorSystem.create();
        cachingActorTestProbe = TestProbe.apply(actorSystem);
        pubSubMediatorProbe = TestProbe.apply(actorSystem);
    }

    @After
    public void tearDown() {
        if (actorSystem != null) {
            TestKit.shutdownActorSystem(actorSystem);
        }
        actorSystem = null;
    }

    @Test
    public void getPolicyEnforcerWithNullIdReturnsEmptyOptional() {
        final ActorSystem system = mock(ActorSystem.class);
        when(system.actorOf(any())).thenReturn(cachingActorTestProbe.ref());
        final var underTest = new CachingPolicyEnforcerProvider(
                system,
                cache,
                blockedNamespaces,
                pubSubMediatorProbe.ref()
        );

        new TestKit(actorSystem) {{
            final var policyEnforcer = underTest.getPolicyEnforcer(null).toCompletableFuture().join();
            assertThat(policyEnforcer).isEmpty();
            cachingActorTestProbe.expectNoMsg();
        }};

    }

    @Test
    public void getPolicyEnforcerAsksCachingActorForPolicyEnforcer() {
        final ActorSystem system = mock(ActorSystem.class);
        when(system.actorOf(any())).thenReturn(cachingActorTestProbe.ref());
        final var underTest = new CachingPolicyEnforcerProvider(
                system,
                cache,
                blockedNamespaces,
                pubSubMediatorProbe.ref()
        );

        new TestKit(actorSystem) {{
            final PolicyId policyId = PolicyId.generateRandom();
            final var policyEnforcer = underTest.getPolicyEnforcer(policyId).toCompletableFuture();
            cachingActorTestProbe.expectMsg(policyId);
            final PolicyEnforcer enforcer = mock(PolicyEnforcer.class);
            cachingActorTestProbe.reply(Optional.of(enforcer));
            assertThat(policyEnforcer.join()).contains(enforcer);
        }};

    }

    @Test
    public void getPolicyEnforcerReturnsEmptyOptionalIfChildActorReturnsOptionalOfWrongType() {
        final ActorSystem system = mock(ActorSystem.class);
        when(system.actorOf(any())).thenReturn(cachingActorTestProbe.ref());
        final var underTest = new CachingPolicyEnforcerProvider(
                system,
                cache,
                blockedNamespaces,
                pubSubMediatorProbe.ref()
        );

        new TestKit(actorSystem) {{
            final PolicyId policyId = PolicyId.generateRandom();
            final var policyEnforcer = underTest.getPolicyEnforcer(policyId).toCompletableFuture();
            cachingActorTestProbe.expectMsg(policyId);
            cachingActorTestProbe.reply(Optional.of("wrong"));
            assertThat(policyEnforcer.join()).isEmpty();
        }};

    }

    @Test
    public void getPolicyEnforcerReturnsEmptyOptionalIfChildActorReturnsEmptyOptional() {
        final ActorSystem system = mock(ActorSystem.class);
        when(system.actorOf(any())).thenReturn(cachingActorTestProbe.ref());
        final var underTest = new CachingPolicyEnforcerProvider(
                system,
                cache,
                blockedNamespaces,
                pubSubMediatorProbe.ref()
        );

        new TestKit(actorSystem) {{
            final PolicyId policyId = PolicyId.generateRandom();
            final var policyEnforcer = underTest.getPolicyEnforcer(policyId).toCompletableFuture();
            cachingActorTestProbe.expectMsg(policyId);
            cachingActorTestProbe.reply(Optional.empty());
            assertThat(policyEnforcer.join()).isEmpty();
        }};

    }

    @Test
    public void getPolicyEnforcerFromCache() {
        final var underTest = new CachingPolicyEnforcerProvider(
                actorSystem,
                cache,
                blockedNamespaces,
                pubSubMediatorProbe.ref()
        );

        new TestKit(actorSystem) {{
            final PolicyEnforcer enforcer = mock(PolicyEnforcer.class);
            final PolicyId policyId = PolicyId.generateRandom();
            final var enforcerResponseFromCache =
                    CompletableFuture.completedFuture(Optional.of(Entry.of(0L, enforcer)));
            when(cache.get(policyId)).thenReturn(enforcerResponseFromCache);

            final var policyEnforcer = underTest.getPolicyEnforcer(policyId).toCompletableFuture();
            assertThat(policyEnforcer.join()).contains(enforcer);
        }};

    }

    @Test
    public void getPolicyEnforcerWhenAbsenceWasCached() {
        final var underTest = new CachingPolicyEnforcerProvider(
                actorSystem,
                cache,
                blockedNamespaces,
                pubSubMediatorProbe.ref()
        );

        new TestKit(actorSystem) {{
            final PolicyId policyId = PolicyId.generateRandom();
            when(cache.get(policyId)).thenReturn(CompletableFuture.completedFuture(Optional.of(Entry.nonexistent())));

            final var policyEnforcer = underTest.getPolicyEnforcer(policyId).toCompletableFuture();
            assertThat(policyEnforcer.join()).isEmpty();
        }};

    }

    @Test
    public void policyTagInvalidatesCacheOfPolicy() {
        new CachingPolicyEnforcerProvider(
                actorSystem,
                cache,
                blockedNamespaces,
                pubSubMediatorProbe.ref()
        );

        new TestKit(actorSystem) {{
            pubSubMediatorProbe.expectMsgClass(DistributedPubSubMediator.Subscribe.class);
            final ActorRef cachingActor = pubSubMediatorProbe.lastSender();

            final PolicyId policyId = PolicyId.generateRandom();
            cachingActor.tell(PolicyTag.of(policyId, 1234L), ActorRef.noSender());

            verify(cache, timeout(3000)).invalidate(policyId);
        }};

    }

    @Test
    public void blockedNamespacesChangeInvalidatesCacheOfPolicy() {
        new CachingPolicyEnforcerProvider(
                actorSystem,
                cache,
                blockedNamespaces,
                pubSubMediatorProbe.ref()
        );

        new TestKit(actorSystem) {{
            pubSubMediatorProbe.expectMsgClass(DistributedPubSubMediator.Subscribe.class);
            final ActorRef cachingActor = pubSubMediatorProbe.lastSender();
            final PolicyEnforcer enforcer = mock(PolicyEnforcer.class);
            final Map<PolicyId, Entry<PolicyEnforcer>> cacheContent = Map.of(
                    PolicyId.of("namespace1", "foo"), Entry.of(0L, enforcer),
                    PolicyId.of("namespace3", "foo"), Entry.of(0L, enforcer),
                    PolicyId.of("namespace4", "foo"), Entry.of(0L, enforcer)
            );
            when(cache.asMap()).thenReturn(new ConcurrentHashMap<>(cacheContent));

            final Replicator.Changed changed = mock(Replicator.Changed.class);
            final ORSet<String> orSet = ORSet.<String>empty()
                    .add(mock(SelfUniqueAddress.class), "namespace1")
                    .add(mock(SelfUniqueAddress.class), "namespace2")
                    .add(mock(SelfUniqueAddress.class), "namespace4");
            when(changed.dataValue()).thenReturn(orSet);
            cachingActor.tell(changed, ActorRef.noSender());
            verify(cache, timeout(3000)).asMap();
            verify(cache).invalidate(PolicyId.of("namespace1", "foo"));
            verify(cache).invalidate(PolicyId.of("namespace4", "foo"));
            verifyNoMoreInteractions(cache);
        }};

    }

}
