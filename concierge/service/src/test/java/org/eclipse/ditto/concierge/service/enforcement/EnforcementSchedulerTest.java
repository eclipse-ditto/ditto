/*
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.concierge.service.enforcement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.eclipse.ditto.base.model.headers.DittoHeaders;
import org.eclipse.ditto.base.model.headers.WithDittoHeaders;
import org.eclipse.ditto.policies.model.PolicyId;
import org.eclipse.ditto.things.model.ThingId;
import org.eclipse.ditto.internal.utils.akka.logging.ThreadSafeDittoLoggingAdapter;
import org.eclipse.ditto.things.model.signals.commands.modify.ModifyPolicyId;
import org.eclipse.ditto.things.model.signals.commands.query.RetrieveThing;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.TestProbe;
import akka.testkit.javadsl.TestKit;
import scala.concurrent.duration.FiniteDuration;

public final class EnforcementSchedulerTest {

    private static ActorSystem actorSystem;

    private ActorRef underTest;

    @BeforeClass
    public static void beforeClass() {
        actorSystem = ActorSystem.create();
    }

    @AfterClass
    public static void afterClass() {
        if (actorSystem != null) {
            actorSystem.terminate();
            actorSystem = null;
        }
    }

    @Before
    public void setup() {
        underTest = actorSystem.actorOf(EnforcementScheduler.props());
    }

    @Test
    public void testOrdering() {
        new TestKit(actorSystem) {{
            final TestProbe deadLetterProbe = TestProbe.apply(actorSystem);
            final TestProbe pubSubProbe = TestProbe.apply(actorSystem);
            final TestProbe conciergeForwarderProbe = TestProbe.apply(actorSystem);
            final TestProbe receiverProbe = TestProbe.apply(actorSystem);
            final ThreadSafeDittoLoggingAdapter mockLogger = Mockito.mock(ThreadSafeDittoLoggingAdapter.class);
            doAnswer(invocation -> mockLogger).when(mockLogger).withCorrelationId(any(DittoHeaders.class));
            doAnswer(invocation -> mockLogger).when(mockLogger).withCorrelationId(any(WithDittoHeaders.class));
            doAnswer(invocation -> mockLogger).when(mockLogger).withCorrelationId(any(CharSequence.class));
            final Contextual<WithDittoHeaders> baseContextual = Contextual.forActor(getRef(), deadLetterProbe.ref(),
                    pubSubProbe.ref(), conciergeForwarderProbe.ref(),
                    Duration.ofSeconds(10), mockLogger,
                    null
            );
            final ThingId thingId = ThingId.of("busy", "thing");
            final PolicyId policyId = PolicyId.of("some", "policy");
            final PolicyId policyId2 = PolicyId.of("other", "policy");

            // First command
            final RetrieveThing retrieveThing1 = RetrieveThing.of(thingId, DittoHeaders.empty());

            // Second command
            final ModifyPolicyId modifyPolicyId1 =
                    ModifyPolicyId.of(thingId, policyId, DittoHeaders.empty());
            // Third command
            final RetrieveThing retrieveThing2 = RetrieveThing.of(thingId, DittoHeaders.empty());

            // Fourth command
            final ModifyPolicyId modifyPolicyId2 =
                    ModifyPolicyId.of(thingId, policyId2, DittoHeaders.empty());

            final Supplier<CompletionStage<Contextual<RetrieveThing>>> delayedRetrieveThing =
                    () -> CompletableFuture.supplyAsync(() -> {
                        try {
                            TimeUnit.SECONDS.sleep(3);

                            return baseContextual.setMessage(retrieveThing1).withReceiver(receiverProbe.ref());
                        } catch (final InterruptedException e) {
                            throw new IllegalStateException("Sleep should not be interrupted.");
                        }
                    });

            final Supplier<CompletionStage<Contextual<ModifyPolicyId>>> delayedModifyPolicyId =
                    () -> CompletableFuture.supplyAsync(() -> {
                        try {
                            TimeUnit.SECONDS.sleep(3);

                            return baseContextual.setMessage(modifyPolicyId1).withReceiver(receiverProbe.ref());
                        } catch (final InterruptedException e) {
                            throw new IllegalStateException("Sleep should not be interrupted.");
                        }
                    });

            final Supplier<CompletionStage<Contextual<RetrieveThing>>> immediateRetrieveThing =
                    () -> CompletableFuture.completedFuture(
                            baseContextual.setMessage(retrieveThing2).withReceiver(receiverProbe.ref())
                    );

            final Supplier<CompletionStage<Contextual<ModifyPolicyId>>> immediateModifyPolicyId =
                    () -> CompletableFuture.completedFuture(
                            baseContextual.setMessage(modifyPolicyId2).withReceiver(receiverProbe.ref())
                    );

            final EnforcementTask retrieveThing1Task = EnforcementTask.of(thingId, false, delayedRetrieveThing);
            final EnforcementTask retrieveThing1TaskSpy = Mockito.spy(retrieveThing1Task);

            final EnforcementTask modifyPolicyId1Task = EnforcementTask.of(thingId, true, delayedModifyPolicyId);
            final EnforcementTask modifyPolicyId1TaskSpy = Mockito.spy(modifyPolicyId1Task);

            final EnforcementTask retrieveThing2Task = EnforcementTask.of(thingId, false, immediateRetrieveThing);
            final EnforcementTask retrieveThing2TaskSpy = Mockito.spy(retrieveThing2Task);

            final EnforcementTask modifyPolicyId2Task = EnforcementTask.of(thingId, true, immediateModifyPolicyId);
            final EnforcementTask modifyPolicyId2TaskSpy = Mockito.spy(modifyPolicyId2Task);

            final InOrder inOrder =
                    inOrder(retrieveThing1TaskSpy, modifyPolicyId1TaskSpy, retrieveThing2TaskSpy,
                            modifyPolicyId2TaskSpy);

            underTest.tell(retrieveThing1TaskSpy, getRef());
            underTest.tell(modifyPolicyId1TaskSpy, getRef());
            underTest.tell(retrieveThing2TaskSpy, getRef());
            underTest.tell(modifyPolicyId2TaskSpy, getRef());

            inOrder.verify(retrieveThing1TaskSpy, timeout(2000)).start();
            // Ensures that modifyPolicyId1 is scheduled without waiting for retrieveThing1 being finished.
            inOrder.verify(modifyPolicyId1TaskSpy, timeout(2000)).start();
            // Ensures that retrieveThing2 is blocked by modifyPolicyID1 which changes authorization and has a 3 second duration
            verify(retrieveThing2TaskSpy, after(2000).never()).start();
            receiverProbe.expectMsg(FiniteDuration.create(5, TimeUnit.SECONDS), retrieveThing1);
            receiverProbe.expectMsg(modifyPolicyId1);

            inOrder.verify(retrieveThing2TaskSpy, timeout(2000)).start();
            inOrder.verify(modifyPolicyId2TaskSpy, timeout(2000)).start();
            receiverProbe.expectMsg(retrieveThing2);
            receiverProbe.expectMsg(modifyPolicyId2);
        }};
    }

}
