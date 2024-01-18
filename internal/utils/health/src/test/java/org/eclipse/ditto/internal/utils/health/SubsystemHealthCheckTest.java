/*
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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
package org.eclipse.ditto.internal.utils.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.japi.pf.ReceiveBuilder;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SubsystemHealthCheckTest {

    private static final String TEST_ACTOR_READY = "ready";
    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofMillis(500);

    /**
     * Name of root actor in tests. Must end with "Root" to be recognized by {@link SubsystemHealthCheck}.
     */
    private static final String ROOT_ACTOR_NAME = "testRoot";

    private ActorSystem actorSystem;
    private SubsystemHealthCheck subsystemHealthCheck;

    @Before
    public void setUp() {
        actorSystem = ActorSystem.create();
        subsystemHealthCheck = new SubsystemHealthCheck(actorSystem, HEALTH_CHECK_TIMEOUT);
    }

    @After
    public void tearDown() {
        if (actorSystem != null) {
            actorSystem.terminate();
        }
    }

    @Test
    public void testCheckReturnsTrueWhenHealthCheckingActorReturnsUp() {
        new TestKit(actorSystem) {{
            final var statusInfo = StatusInfo.fromStatus(StatusInfo.Status.UP);
            actorSystem.actorOf(TestRootActor.props(statusInfo, getRef()), ROOT_ACTOR_NAME);

            expectMsg(TEST_ACTOR_READY);

            assertHealthCheckResult(subsystemHealthCheck, true);
        }};
    }

    @Test
    public void testCheckReturnsTrueWhenHealthCheckingActorReturnsUnknown() {
        new TestKit(actorSystem) {{
            final var statusInfo = StatusInfo.fromStatus(StatusInfo.Status.UNKNOWN);
            actorSystem.actorOf(TestRootActor.props(statusInfo, getRef()), ROOT_ACTOR_NAME);

            expectMsg(TEST_ACTOR_READY);

            assertHealthCheckResult(subsystemHealthCheck, true);
        }};
    }

    @Test
    public void testCheckReturnsFalseWhenHealthCheckingActorReturnsDown() {
        new TestKit(actorSystem) {{
            final var statusInfo = StatusInfo.fromStatus(StatusInfo.Status.DOWN);
            actorSystem.actorOf(TestRootActor.props(statusInfo, getRef()), ROOT_ACTOR_NAME);

            expectMsg(TEST_ACTOR_READY);

            assertHealthCheckResult(subsystemHealthCheck, false);
        }};
    }

    @Test
    public void testCheckReturnsFalseWhenHealthCheckingActorDoesNotExist() {
        new TestKit(actorSystem) {{
            assertHealthCheckResult(subsystemHealthCheck, false);
        }};
    }

    private static void assertHealthCheckResult(SubsystemHealthCheck subsystemHealthCheck, boolean expected) {
        final var healthCheckResultFuture = subsystemHealthCheck.get();
        Awaitility.await()
                .atMost(Duration.ofNanos(2 * HEALTH_CHECK_TIMEOUT.toNanos()))
                .until(() -> healthCheckResultFuture.toCompletableFuture().isDone());
        assertThat(healthCheckResultFuture.toCompletableFuture())
                .isCompletedWithValue(expected);
    }

    private static class TestRootActor extends AbstractActor {

        private TestRootActor(final StatusInfo statusInfo, final ActorRef actorToNotify) {
            getContext().actorOf(TestHealthCheckingActor.props(statusInfo, actorToNotify),
                    DefaultHealthCheckingActorFactory.ACTOR_NAME);
        }

        public static Props props(final StatusInfo statusInfo, final ActorRef actorToNotify) {
            return Props.create(TestRootActor.class, statusInfo, actorToNotify);
        }

        @Override
        public Receive createReceive() {
            return ReceiveBuilder.create()
                    .matchAny(this::unhandled)
                    .build();
        }

    }

    private static class TestHealthCheckingActor extends AbstractActor {

        private final StatusInfo statusInfo;
        private final ActorRef actorToNotify;

        private TestHealthCheckingActor(final StatusInfo statusInfo, final ActorRef actorToNotify) {
            this.statusInfo = statusInfo;
            this.actorToNotify = actorToNotify;
        }

        public static Props props(final StatusInfo statusInfo, final ActorRef actorToNotify) {
            return Props.create(TestHealthCheckingActor.class, statusInfo, actorToNotify);
        }

        @Override
        public Receive createReceive() {
            return ReceiveBuilder.create()
                    .match(RetrieveHealth.class, this::returnStatusInfo)
                    .build();
        }

        @Override
        public void preStart() {
            actorToNotify.tell(TEST_ACTOR_READY, getSelf());
        }

        private void returnStatusInfo(final RetrieveHealth command) {
            getSender().tell(this.statusInfo, getSelf());
        }
    }

}