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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.internal.utils.akka.ActorSystemResource;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.internal.utils.cacheloaders.config.DefaultAskWithRetryConfig;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyPolicyId;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import scala.concurrent.duration.FiniteDuration;

public final class EnforcementSchedulerTest {

    @ClassRule
    public static final ActorSystemResource ACTOR_SYSTEM_RESOURCE =
            ActorSystemResource.newInstance(ConfigFactory.load("test"));

    private ActorRef underTest;

    @Before
    public void setup() {
        underTest = ACTOR_SYSTEM_RESOURCE.newActor(EnforcementScheduler.props());
    }

    @Test
    public void testOrdering() {
        final var testKit = ACTOR_SYSTEM_RESOURCE.newTestKit();
        final var pubSubProbe = ACTOR_SYSTEM_RESOURCE.newTestProbe();
        final var commandForwarderProbe = ACTOR_SYSTEM_RESOURCE.newTestProbe();
        final var receiver = ACTOR_SYSTEM_RESOURCE.newTestKit();
        final var mockLogger = mock(ThreadSafeDittoLoggingAdapter.class);
        doAnswer(invocation -> mockLogger).when(mockLogger).withCorrelationId(any(DittoHeaders.class));
        doAnswer(invocation -> mockLogger).when(mockLogger).withCorrelationId(any(WithDittoHeaders.class));
        doAnswer(invocation -> mockLogger).when(mockLogger).withCorrelationId(any(CharSequence.class));
        final Contextual<WithDittoHeaders> baseContextual = Contextual.forActor(testKit.getRef(),
                ACTOR_SYSTEM_RESOURCE.getActorSystem(),
                pubSubProbe.ref(),
                commandForwarderProbe.ref(),
                DefaultAskWithRetryConfig.of(ConfigFactory.empty(), "test"),
                mockLogger);
        final var thingId = ThingId.of("busy", "thing");
        final var policyId = PolicyId.of("some", "policy");
        final var policyId2 = PolicyId.of("other", "policy");

        // First command
        final var retrieveThing1 = RetrieveThing.of(thingId, DittoHeaders.empty());

        // Second command
        final var modifyPolicyId1 = ModifyPolicyId.of(thingId, policyId, DittoHeaders.empty());
        // Third command
        final var retrieveThing2 = RetrieveThing.of(thingId, DittoHeaders.empty());

        // Fourth command
        final var modifyPolicyId2 = ModifyPolicyId.of(thingId, policyId2, DittoHeaders.empty());

        final Supplier<CompletionStage<Contextual<RetrieveThing>>> delayedRetrieveThing =
                () -> CompletableFuture.supplyAsync(() -> {
                    try {
                        TimeUnit.SECONDS.sleep(3);

                        return baseContextual.setMessage(retrieveThing1).withReceiver(receiver.getRef());
                    } catch (final InterruptedException e) {
                        throw new IllegalStateException("Sleep should not be interrupted.");
                    }
                });

        final Supplier<CompletionStage<Contextual<ModifyPolicyId>>> delayedModifyPolicyId =
                () -> CompletableFuture.supplyAsync(() -> {
                    try {
                        TimeUnit.SECONDS.sleep(3);

                        return baseContextual.setMessage(modifyPolicyId1).withReceiver(receiver.getRef());
                    } catch (final InterruptedException e) {
                        throw new IllegalStateException("Sleep should not be interrupted.");
                    }
                });

        final Supplier<CompletionStage<Contextual<RetrieveThing>>> immediateRetrieveThing =
                () -> CompletableFuture.completedFuture(baseContextual.setMessage(retrieveThing2)
                        .withReceiver(receiver.getRef()));

        final Supplier<CompletionStage<Contextual<ModifyPolicyId>>> immediateModifyPolicyId =
                () -> CompletableFuture.completedFuture(baseContextual.setMessage(modifyPolicyId2)
                        .withReceiver(receiver.getRef()));

        final var retrieveThing1Task = EnforcementTask.of(thingId, false, delayedRetrieveThing);
        final var retrieveThing1TaskSpy = spy(retrieveThing1Task);

        final var modifyPolicyId1Task = EnforcementTask.of(thingId, true, delayedModifyPolicyId);
        final var modifyPolicyId1TaskSpy = spy(modifyPolicyId1Task);

        final var retrieveThing2Task = EnforcementTask.of(thingId, false, immediateRetrieveThing);
        final var retrieveThing2TaskSpy = spy(retrieveThing2Task);

        final var modifyPolicyId2Task = EnforcementTask.of(thingId, true, immediateModifyPolicyId);
        final var modifyPolicyId2TaskSpy = spy(modifyPolicyId2Task);

        final var inOrder =
                inOrder(retrieveThing1TaskSpy, modifyPolicyId1TaskSpy, retrieveThing2TaskSpy, modifyPolicyId2TaskSpy);

        underTest.tell(retrieveThing1TaskSpy, testKit.getRef());
        underTest.tell(modifyPolicyId1TaskSpy, testKit.getRef());
        underTest.tell(retrieveThing2TaskSpy, testKit.getRef());
        underTest.tell(modifyPolicyId2TaskSpy, testKit.getRef());

        inOrder.verify(retrieveThing1TaskSpy, timeout(2000)).start();
        // Ensures that modifyPolicyId1 is scheduled without waiting for retrieveThing1 being finished.
        inOrder.verify(modifyPolicyId1TaskSpy, timeout(2000)).start();
        // Ensures that retrieveThing2 is blocked by modifyPolicyID1 which changes authorization and has a 3-second duration
        verify(retrieveThing2TaskSpy, after(2000).never()).start();
        receiver.expectMsg(FiniteDuration.create(5, TimeUnit.SECONDS), retrieveThing1);
        receiver.expectMsg(modifyPolicyId1);

        inOrder.verify(retrieveThing2TaskSpy, timeout(2000)).start();
        inOrder.verify(modifyPolicyId2TaskSpy, timeout(2000)).start();
        receiver.expectMsg(retrieveThing2);
        receiver.expectMsg(modifyPolicyId2);
    }

}
