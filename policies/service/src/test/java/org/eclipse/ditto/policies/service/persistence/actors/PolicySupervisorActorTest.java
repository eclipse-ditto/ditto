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
package org.eclipse.ditto.policies.service.persistence.actors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.internal.utils.cluster.StopShardedActor;
import org.eclipse.ditto.internal.utils.namespaces.BlockedNamespaces;
import org.eclipse.ditto.internal.utils.persistence.mongo.streaming.MongoReadJournal;
import org.eclipse.ditto.internal.utils.pubsub.DistributedPub;
import org.eclipse.ditto.internal.utils.tracing.DittoTracingInitResource;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcer;
import org.eclipse.ditto.policies.enforcement.PolicyEnforcerProvider;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.policies.model.enforcers.Enforcer;
import org.eclipse.ditto.policies.model.signals.announcements.PolicyAnnouncement;
import org.eclipse.ditto.policies.model.signals.commands.exceptions.PolicyNotAccessibleException;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicy;
import org.eclipse.ditto.policies.model.signals.commands.modify.CreatePolicyResponse;
import org.eclipse.ditto.policies.model.signals.commands.query.RetrievePolicy;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import akka.stream.Attributes;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Tests {@link PolicySupervisorActor}.
 */
public final class PolicySupervisorActorTest extends PersistenceActorTestBase {

    @ClassRule
    public static final DittoTracingInitResource DITTO_TRACING_INIT_RESOURCE =
            DittoTracingInitResource.disableDittoTracing();

    @SuppressWarnings("unchecked")
    @Mock
    public DistributedPub<PolicyAnnouncement<?>> pub = mock(DistributedPub.class);

    @Mock
    public BlockedNamespaces blockedNamespaces = mock(BlockedNamespaces.class);

    @Mock
    public PolicyEnforcerProvider policyEnforcerProvider = mock(PolicyEnforcerProvider.class);

    @Mock
    public PolicyEnforcer policyEnforcer = mock(PolicyEnforcer.class);

    @Mock
    public Enforcer enforcer = mock(Enforcer.class);

    @Before
    public void setup() {
        setUpBase();
    }


    @Test
    public void stopNonexistentPolicy() {
        new TestKit(actorSystem) {{
            final PolicyId policyId = PolicyId.of("test.ns", "stopNonexistentPolicy");
            final var props = PolicySupervisorActor.props(pubSubMediator, pub, blockedNamespaces,
                    policyEnforcerProvider, Mockito.mock(MongoReadJournal.class));
            final var underTest = watch(childActorOf(props, policyId.toString()));
            underTest.tell(new StopShardedActor(), getRef());
            expectTerminated(underTest);
        }};
    }

    @Test
    public void stopAfterRetrievingNonexistentPolicy() {
        new TestKit(actorSystem) {{
            final PolicyId policyId = PolicyId.of("test.ns", "retrieveNonexistentPolicy");
            final var props = PolicySupervisorActor.props(pubSubMediator, pub, blockedNamespaces,
                    policyEnforcerProvider, Mockito.mock(MongoReadJournal.class));
            final var underTest = watch(childActorOf(props, policyId.toString()));
            final var probe = TestProbe.apply(actorSystem);
            final var retrievePolicy = RetrievePolicy.of(policyId, DittoHeaders.empty());
            mockPolicyEnforcer(policyId);

            underTest.tell(retrievePolicy, probe.ref());
            underTest.tell(new StopShardedActor(), getRef());
            expectTerminated(underTest);
            probe.expectMsgClass(PolicyNotAccessibleException.class);
        }};
    }

    @Test
    public void stopAfterRetrievingExistingPolicy() {
        actorSystem.eventStream().setLogLevel(Attributes.LogLevels$.MODULE$.Debug());
        new TestKit(actorSystem) {
            {
                final var policy = createPolicyWithRandomId();
                final var policyId = policy.getEntityId().orElseThrow();
                final var props = PolicySupervisorActor.props(pubSubMediator, pub, blockedNamespaces,
                        policyEnforcerProvider, Mockito.mock(MongoReadJournal.class));
                final var underTest = watch(childActorOf(props, policyId.toString()));
                final var probe = TestProbe.apply(actorSystem);

                final var createPolicy = CreatePolicy.of(policy, dittoHeadersV2);
                underTest.tell(createPolicy, probe.ref());
                final var response = probe.expectMsgClass(CreatePolicyResponse.class);
                assertThat(response.getPolicyCreated().orElseThrow().getEntriesSet()).isEqualTo(policy.getEntriesSet());

                // Tolerate some delay between policy commands
                expectNoMsg();

                final var retrievePolicy = RetrievePolicy.of(policyId, DittoHeaders.empty());
                mockPolicyEnforcer(policyId);
                underTest.tell(retrievePolicy, probe.ref());
                underTest.tell(new StopShardedActor(), getRef());
                expectTerminated(Duration.of(1200, ChronoUnit.SECONDS), underTest);
                probe.expectMsgClass(PolicyNotAccessibleException.class);
            }
        };
    }

    private void mockPolicyEnforcer(PolicyId policyId) {
        when(enforcer.hasPartialPermissions(any(), any(), any())).thenReturn(Boolean.TRUE);
        when(policyEnforcerProvider.getPolicyEnforcer(policyId))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(policyEnforcer)));
        when(policyEnforcer.getEnforcer()).thenReturn(enforcer);
    }
}
